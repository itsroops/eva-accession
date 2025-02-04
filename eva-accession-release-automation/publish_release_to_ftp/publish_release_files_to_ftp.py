# Copyright 2020 EMBL - European Bioinformatics Institute
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# Given a taxonomy, this script publishes data from the NFS staging folder to the public FTP release folder
# and creates the layout as shown in the link below:
# https://docs.google.com/presentation/d/1cishRa6P6beIBTP8l1SgJfz71vQcCm5XLmSA8Hmf8rw/edit#slide=id.g63fd5cd489_0_0

import click
import glob
import os
import psycopg2

from publish_release_to_ftp.create_assembly_name_symlinks import create_assembly_name_symlinks
from ebi_eva_common_pyutils.command_utils import run_command_with_output
from ebi_eva_common_pyutils.config_utils import get_pg_metadata_uri_for_eva_profile, get_properties_from_xml_file
from ebi_eva_common_pyutils.logger import logging_config
from ebi_eva_common_pyutils.pg_utils import get_all_results_for_query
from run_release_in_embassy.run_release_for_species import get_common_release_properties
from run_release_in_embassy.release_metadata import release_vcf_file_categories, release_text_file_categories

by_assembly_folder_name = "by_assembly"
by_species_folder_name = "by_species"
release_file_types_to_be_checksummed = ("vcf.gz", "txt.gz", "csi")
readme_general_info_file = "README_release_general_info.txt"
readme_known_issues_file = "README_release_known_issues.txt"
release_top_level_files_to_copy = ("README_release_known_issues.txt", readme_general_info_file,
                                   "species_name_mapping.tsv")
unmapped_ids_file_regex = "*_unmapped_ids.txt.gz"
logger = logging_config.get_logger(__name__)


class ReleaseProperties:
    def __init__(self, common_release_properties_file):
        """
        Get release properties from common release properties file
        """
        common_release_properties = get_common_release_properties(common_release_properties_file)
        self.private_config_xml_file = common_release_properties["private-config-xml-file"]
        self.release_version = common_release_properties["release-version"]
        self.release_species_inventory_table = common_release_properties["release-species-inventory-table"]
        self.staging_release_folder = common_release_properties["release-folder"]
        self.public_ftp_release_base_folder = common_release_properties["public-ftp-release-base-folder"]
        self.public_ftp_current_release_folder = os.path.join(self.public_ftp_release_base_folder,
                                                              "release_{0}".format(self.release_version))
        self.public_ftp_previous_release_folder = os.path.join(self.public_ftp_release_base_folder,
                                                               "release_{0}".format(self.release_version - 1))


def get_current_release_folder_for_taxonomy(taxonomy_id, release_properties, metadata_connection_handle):
    """
    Get info on current and previous release assemblies for the given taxonomy
    """

    def info_for_release_version(version):
        results = get_all_results_for_query(metadata_connection_handle,
                                            "select distinct release_folder_name from {0} "
                                            "where taxonomy = '{1}' "
                                            "and release_version = {2}"
                                            .format(release_properties.release_species_inventory_table, taxonomy_id,
                                                    version))
        return results[0][0] if len(results) > 0 else None

    current_release_folder = info_for_release_version(release_properties.release_version)

    return current_release_folder


def get_release_assemblies_info_for_taxonomy(taxonomy_id, release_properties, metadata_connection_handle):
    """
    Get info on current and previous release assemblies for the given taxonomy
    """
    results = get_all_results_for_query(metadata_connection_handle, "select row_to_json(row) from "
                                                                    "(select * from {0} "
                                                                    "where taxonomy = '{1}' "
                                                                    "and release_version in ({2}, {2} - 1)) row"
                                        .format(release_properties.release_species_inventory_table, taxonomy_id,
                                                release_properties.release_version))
    if len(results) == 0:
        raise Exception("Could not find assemblies pertaining to taxonomy ID: " + taxonomy_id)
    return [result[0] for result in results]


def get_release_file_list_for_assembly(release_assembly_info):
    """
    Get list of release files at assembly level
    for example, see here, ftp://ftp.ebi.ac.uk/pub/databases/eva/rs_releases/release_1/by_assembly/GCA_000001515.4/)
    """
    assembly_accession = release_assembly_info["assembly_accession"]
    vcf_files = ["{0}_{1}.vcf.gz".format(assembly_accession, category) for category in release_vcf_file_categories]
    text_files = ["{0}_{1}.txt.gz".format(assembly_accession, category) for category in release_text_file_categories]
    csi_files = ["{0}.csi".format(filename) for filename in vcf_files]
    release_file_list = vcf_files + text_files + csi_files + ["README_rs_ids_counts.txt"]
    return sorted(release_file_list)


def get_folder_path_for_assembly(release_folder_base, assembly_accession):
    return os.path.join(release_folder_base, by_assembly_folder_name, assembly_accession)


def get_folder_path_for_species(release_folder_base, species_release_folder_name):
    return os.path.join(release_folder_base, by_species_folder_name, species_release_folder_name)


def create_symlink_to_assembly_folder_from_species_folder(current_release_assembly_info, release_properties,
                                                          public_release_assembly_folder):
    """
    Create linkage to assembly folders from the species folder
    See FTP layout linkage between by_assembly and by_species folders in the link below:
    https://docs.google.com/presentation/d/1cishRa6P6beIBTP8l1SgJfz71vQcCm5XLmSA8Hmf8rw/edit#slide=id.g63fd5cd489_0_0
    """
    assembly_accession = current_release_assembly_info["assembly_accession"]
    species_release_folder_name = current_release_assembly_info["release_folder_name"]
    public_release_species_folder = os.path.join(release_properties.public_ftp_current_release_folder,
                                                 by_species_folder_name, species_release_folder_name)
    run_command_with_output("Creating symlink from species folder {0} to assembly folder {1}"
                            .format(public_release_species_folder, public_release_assembly_folder),
                            'bash -c "cd {1} && ln -sfT {0} {1}/{2}"'.format(
                                os.path.relpath(public_release_assembly_folder, public_release_species_folder),
                                public_release_species_folder, assembly_accession))


def recreate_public_release_assembly_folder(assembly_accession, public_release_assembly_folder):
    run_command_with_output("Removing release folder if it exists for {0}...".format(assembly_accession),
                            "rm -rf " + public_release_assembly_folder)
    run_command_with_output("Creating release folder for {0}...".format(assembly_accession),
                            "mkdir -p " + public_release_assembly_folder)


def copy_current_assembly_data_to_ftp(current_release_assembly_info, release_properties,
                                      public_release_assembly_folder):
    assembly_accession = current_release_assembly_info["assembly_accession"]
    species_release_folder_name = current_release_assembly_info["release_folder_name"]
    md5sum_output_file = os.path.join(public_release_assembly_folder, "md5checksums.txt")
    run_command_with_output("Removing md5 checksum file {0} for assembly if it exists...".format(md5sum_output_file),
                            "rm -f " + md5sum_output_file)
    recreate_public_release_assembly_folder(assembly_accession, public_release_assembly_folder)

    for filename in get_release_file_list_for_assembly(current_release_assembly_info):
        source_file_path = os.path.join(release_properties.staging_release_folder, species_release_folder_name,
                                        assembly_accession, filename)
        run_command_with_output("Copying {0} to {1}...".format(filename, public_release_assembly_folder),
                                "cp {0} {1}".format(source_file_path, public_release_assembly_folder))
        if filename.endswith(release_file_types_to_be_checksummed):
            md5sum_output = run_command_with_output("Checksumming file {0}...".format(filename),
                                                    "(md5sum {0} | awk '{{ print $1 }}')".format(source_file_path),
                                                    return_process_output=True)
            open(md5sum_output_file, "a").write(md5sum_output.strip() + "\t" +
                                                os.path.basename(source_file_path) + "\n")


def hardlink_to_previous_release_assembly_files_in_ftp(current_release_assembly_info, release_properties):
    assembly_accession = current_release_assembly_info["assembly_accession"]
    public_current_release_assembly_folder = \
        get_folder_path_for_assembly(release_properties.public_ftp_current_release_folder, assembly_accession)
    public_previous_release_assembly_folder = \
        get_folder_path_for_assembly(release_properties.public_ftp_previous_release_folder, assembly_accession)

    if os.path.exists(public_previous_release_assembly_folder):
        recreate_public_release_assembly_folder(assembly_accession, public_current_release_assembly_folder)
        for filename in get_release_file_list_for_assembly(current_release_assembly_info) + ["md5checksums.txt"]:
            file_to_hardlink = "{0}/{1}".format(public_previous_release_assembly_folder, filename)
            if os.path.exists(file_to_hardlink):
                run_command_with_output("Creating hardlink from previous release assembly folder {0} "
                                        "to current release assembly folder {1}"
                                        .format(public_current_release_assembly_folder,
                                                public_previous_release_assembly_folder)
                                        , 'ln -f {0} {1}'.format(file_to_hardlink,
                                                                 public_current_release_assembly_folder))
    else:
        raise Exception("Previous release folder {0} does not exist for assembly!"
                        .format(public_previous_release_assembly_folder))


def publish_assembly_release_files_to_ftp(current_release_assembly_info, release_properties):
    assembly_accession = current_release_assembly_info["assembly_accession"]
    public_release_assembly_folder = \
        get_folder_path_for_assembly(release_properties.public_ftp_current_release_folder, assembly_accession)
    # If a species was processed during this release, copy current release data to FTP
    if current_release_assembly_info["should_be_released"] and \
            current_release_assembly_info["num_rs_to_release"] > 0:
        copy_current_assembly_data_to_ftp(current_release_assembly_info, release_properties,
                                          public_release_assembly_folder)
    else:
        # Since the assembly data is unchanged from the last release, hard-link instead of symlink to older release data
        # so that deleting data in older releases does not impact the newer releases
        # (hard-linking preserves the underlying data for a link until all links to that data are deleted)
        hardlink_to_previous_release_assembly_files_in_ftp(current_release_assembly_info, release_properties)

    # Symlink to release README_general_info file - See layout in the link below:
    # https://docs.google.com/presentation/d/1cishRa6P6beIBTP8l1SgJfz71vQcCm5XLmSA8Hmf8rw/edit#slide=id.g63fd5cd489_0_0
    run_command_with_output("Symlinking to release level {0} and {1} files for assembly {1}"
                            .format(readme_general_info_file, readme_known_issues_file, assembly_accession),
                            'bash -c "cd {1} && ln -sfT {0}/{2} {1}/{2} && ln -sfT {0}/{3} {1}/{3}"'
                            .format(os.path.relpath(release_properties.public_ftp_current_release_folder,
                                                    public_release_assembly_folder), public_release_assembly_folder,
                                    readme_general_info_file, readme_known_issues_file))
    # Create a link from species folder ex: by_species/ovis_aries to point to this assembly folder
    create_symlink_to_assembly_folder_from_species_folder(current_release_assembly_info, release_properties,
                                                          public_release_assembly_folder)


def get_release_assemblies_for_release_version(assemblies_to_process, release_version):
    return list(filter(lambda x: x["release_version"] == release_version, assemblies_to_process))


def copy_unmapped_files(source_folder_to_copy_from, species_current_release_folder_path, copy_from_current_release):
    def copy_file(src, dest, copy_command):
        run_command_with_output("Copying file {0} to {1}...".format(src, dest),
                                "({0} {1} {2})".format(copy_command, src, dest))
    species_level_files_to_copy = (unmapped_ids_file_regex, "md5checksums.txt", "README_unmapped_rs_ids_count.txt")

    # Copy files from current release folder
    if copy_from_current_release:
        for filename in species_level_files_to_copy:
            absolute_file_path = os.path.join(source_folder_to_copy_from, filename)
            copy_file(absolute_file_path, species_current_release_folder_path, "cp")
    else:
        unmapped_variants_files = glob.glob("{0}/{1}".format(source_folder_to_copy_from, unmapped_ids_file_regex))
        assert len(unmapped_variants_files) <= 1, \
            "Multiple unmapped variant files found in source folder: {0}".format(" ".join(unmapped_variants_files))
        assert len(unmapped_variants_files) > 0, \
            "No unmapped variant files found in source folder: {0}".format(source_folder_to_copy_from)
        # Ensure that the species name is properly replaced (ex: mouse_10090 renamed to mus_musculus)
        # in the unmapped variants file name
        unmapped_variants_file_path_current_release = \
            os.path.join(species_current_release_folder_path,
                         unmapped_ids_file_regex.replace("*", os.path.basename(species_current_release_folder_path)))
        copy_file(unmapped_variants_files[0], unmapped_variants_file_path_current_release, "ln -f")
        # Compute MD5 checksum file
        run_command_with_output("Compute MD5 checksum",
                                "(md5sum {0} | awk '{{print $1}}' | xargs -i echo -e '{{}}\t{1}') > {2}"
                                .format(unmapped_variants_file_path_current_release,
                                        os.path.basename(unmapped_variants_file_path_current_release),
                                        os.path.join(species_current_release_folder_path,
                                                     species_level_files_to_copy[1])))
        # Compute unmapped variants count and populate it in the README file
        run_command_with_output("Compute MD5 checksum",
                                "(zcat {0} | grep -v ^# | cut -f1 | sort | uniq | wc -l "
                                "| xargs -i echo -e '# Unique RS ID counts\n{1}\t{{}}') > {2}"
                                .format(unmapped_variants_file_path_current_release,
                                        os.path.basename(unmapped_variants_file_path_current_release),
                                        os.path.join(species_current_release_folder_path,
                                                     species_level_files_to_copy[2])))


def publish_species_level_files_to_ftp(release_properties, species_current_release_folder_name):
    species_staging_release_folder_path = os.path.join(release_properties.staging_release_folder,
                                                       species_current_release_folder_name)
    species_current_release_folder_path = \
        get_folder_path_for_species(release_properties.public_ftp_current_release_folder,
                                    species_current_release_folder_name)
    species_previous_release_folder_path = \
        get_folder_path_for_species(release_properties.public_ftp_previous_release_folder,
                                    species_current_release_folder_name)

    # Determine if the unmapped data should be copied from the current or a previous release
    copy_from_current_release = len(glob.glob(os.path.join(species_staging_release_folder_path,
                                                           unmapped_ids_file_regex))) > 0
    source_folder_to_copy_from = species_current_release_folder_path if copy_from_current_release \
        else species_previous_release_folder_path

    copy_unmapped_files(source_folder_to_copy_from, species_current_release_folder_path, copy_from_current_release)


def publish_release_top_level_files_to_ftp(release_properties):
    grep_command_for_release_level_files = "grep " + " ".join(['-e "{0}"'.format(filename) for filename in
                                                               release_top_level_files_to_copy])
    run_command_with_output("Copying release level files from {0} to {1}...."
                            .format(release_properties.staging_release_folder,
                                    release_properties.public_ftp_current_release_folder),
                            "(find {0} -maxdepth 1 -type f | {1} | xargs -i rsync -av {{}} {2})".format(
                                release_properties.staging_release_folder, grep_command_for_release_level_files,
                                release_properties.public_ftp_current_release_folder))


def create_requisite_folders(release_properties):
    run_command_with_output("Creating by_species folder for the current release...",
                            "mkdir -p " + os.path.join(release_properties.public_ftp_current_release_folder,
                                                       by_species_folder_name))
    run_command_with_output("Creating by_assembly folder for the current release...",
                            "mkdir -p " + os.path.join(release_properties.public_ftp_current_release_folder,
                                                       by_assembly_folder_name))


def create_species_folder(release_properties, species_current_release_folder_name):
    species_current_release_folder_path = \
        get_folder_path_for_species(release_properties.public_ftp_current_release_folder,
                                    species_current_release_folder_name)

    run_command_with_output("Creating species release folder {0}...".format(species_current_release_folder_path),
                            "rm -rf {0} && mkdir {0}".format(species_current_release_folder_path))


def publish_release_files_to_ftp(common_release_properties_file, taxonomy_id):
    release_properties = ReleaseProperties(common_release_properties_file)
    create_requisite_folders(release_properties)
    # Release README, known issues etc.,
    publish_release_top_level_files_to_ftp(release_properties)

    metadata_password = get_properties_from_xml_file("production_processing",
                                                     release_properties.private_config_xml_file)["eva.evapro.password"]
    with psycopg2.connect(get_pg_metadata_uri_for_eva_profile("production_processing",
                                                              release_properties.private_config_xml_file),
                          user="evapro", password=metadata_password) as metadata_connection_handle:
        assemblies_to_process = get_release_assemblies_info_for_taxonomy(taxonomy_id, release_properties,
                                                                         metadata_connection_handle)
        species_has_unmapped_data = "Unmapped" in set([assembly_info["assembly_accession"] for assembly_info in
                                                       assemblies_to_process])

        # Publish species level data
        species_current_release_folder_name = get_current_release_folder_for_taxonomy(taxonomy_id, release_properties,
                                                                                      metadata_connection_handle)

        create_species_folder(release_properties, species_current_release_folder_name)

        # Unmapped variant data is published at the species level
        # because they are not mapped to any assemblies (duh!)
        if species_has_unmapped_data:
            publish_species_level_files_to_ftp(release_properties, species_current_release_folder_name)

        # Publish assembly level data
        for current_release_assembly_info in \
                get_release_assemblies_for_release_version(assemblies_to_process, release_properties.release_version):
            if current_release_assembly_info["assembly_accession"] != "Unmapped":
                publish_assembly_release_files_to_ftp(current_release_assembly_info, release_properties)

        # Symlinks with assembly names in the species folder ex: Sorbi1 -> GCA_000003195.1
        create_assembly_name_symlinks(get_folder_path_for_species(release_properties.public_ftp_current_release_folder,
                                                                  species_current_release_folder_name))


@click.option("--common-release-properties-file", help="ex: /path/to/release/properties.yml", required=True)
@click.option("--taxonomy-id", help="ex: 9913", required=True)
@click.command()
def main(common_release_properties_file, taxonomy_id):
    publish_release_files_to_ftp(common_release_properties_file, taxonomy_id)


if __name__ == "__main__":
    main()
