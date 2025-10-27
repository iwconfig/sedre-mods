# SEDRE Offline Patches

This repository contains patches to fix issues with the offline version of the SEDRE application (part of the Citroën/Peugeot Service Box), allowing it to run correctly and (somewhat) better on modern systems.

These patches are applied through the use of what is known as "class shadowing", where the original class is substituted by a different one occupying the same class path. With our JAR files placed inside `SEDRE_INSTALL_DIR/Resources/Sdr/WEB-INF/lib`, the Tomcat server's classloader loads our versions first, effectively "patching" the application without modifying any original JARs or configuration files like `SEDRE_INSTALL_DIR/Resources/Sdr/WEB-INF/web.xml`.

## Patches

1.  **sdr-multidisc-patch.jar**:

    - Big corporate legacy software usually involves a lot of spagetti code. SEDRE refuses to find any other mounted DVD drives besides disc 1 (on e.g. drive E:\), which meant the user had to eject disc 1 and mount disc 2 on the same drive letter (E:\).

      `ParserServlet.java` fixes the application's inability to find discs other than the primary one specified in `sdr.properties`. It reads a custom `discs.properties` file to locate the correct disc.

      This also means it can read from any path, including UNC paths (network shares), thus eliminating the need for mounting physical drives or ISO files. For instance, individual files can be stored in the cloud and the remote root directory then can be mounted locally using rclone.

    - `GetChoixDescripteurAction.java` fixes the annoying "Please insert disc X" JavaScript alert that appears even when the correct disc is available and read properly. You can just click OK and it's works fine, but patch provides the information needed to not trigger that alert.

    - Requires `discs.properties`.

2.  **sdr-svgfix-patch.jar**:

    - `SvgLoaderServlet.java` fixes an issue where `.svgz` files are not served with the correct `Content-Encoding: gzip` header. This prevents the `not well-formed` error.

    - It also corrects a related issue where some files with a `.svgz` extension are not actually compressed, causing rendering failures. It simply checks each file if it is gzipped or not and applies the header only when needed.

    - Requires `discs.properties`.

## Installation

> [!NOTE]  
> The directory "SEDRE_INSTALL_DIR" mentioned here is referring to either "SEDREAC" (Citroen) or "SEDREAP" (Peugeot).

1. **Build or download the latest patches**

You can build the JARs yourself (see [Build Instructions](#build-instructions)) or use the pre-built packages from the [**latest release.**](https://github.com/iwconfig/sedre-mods/releases/latest)

The JARs need place them in `/path/to/your/SEDRE_INSTALL_DIR/Resources/Sdr/WEB-INF/lib/`, and the `discs.properties` in `SEDRE_INSTALL_DIR/Properties/extconf/`

- **Manual**

  1.  Go to the [**Releases Page**](https://github.com/iwconfig/sedre-mods/releases/latest).
  2.  Download [the `sedre-patches.zip` file.](https://github.com/iwconfig/sedre-mods/releases/latest/download/sedre-patches.zip)
  3.  Extract the contents of the ZIP file directly into the root of your SEDRE installation directory.

- **PowerShell**

  ```powershell
  # Set the path to your SEDRE installation root
  $sedrePath = "C:\Path\To\Your\SEDRE_INSTALL_DIR"

  # Download the latest release
  Invoke-WebRequest -Uri "https://github.com/iwconfig/sedre-mods/releases/latest/download/sedre-patches.zip" -OutFile "sedre-patches.zip"

  # Extract the archive directly into your SEDRE directory, overwriting if necessary
  Expand-Archive -Path "sedre-patches.zip" -DestinationPath $sedrePath -Force
  ```

- **Bash (\*nix, Linux, BSD, macOS, WSL)**

  ```bash
  # Download the latest release
  curl -OL "https://github.com/iwconfig/sedre-mods/releases/latest/download/sedre-patches.zip"

  # Extract the archive directly into your SEDRE directory
  unzip -o sedre-patches.zip -d /path/to/your/SEDRE_INSTALL_DIR/
  ```

2.  **Configure disc paths**
    After installing the patches, you must configure the location of your discs by editing the `SEDRE_INSTALL_DIR/Properties/extconf/discs.properties` file.

    If you're building yourself and/or it doesn't exist, you can copy the `discs.properties.template` file.

    **Path separators must be forward slashes (`/`), even on Windows.**

    ```properties
    # Example discs.properties:
    # Paths to the root of each SEDRE disc.
    disc.1=E:/some/dir/
    disc.2=//server/share/sedre_disc_2/
    ```

3.  **Restart the SEDRE server application**

## Build Instructions

### Prerequisites

- A Unix-like environment (e.g. Linux, WSL, BSD, macOS). Possibly git bash or cygwin will also work.
- A Java Development Kit (JDK) compatible with Java 1.4 source/target. **OpenJDK 8** is the last version of OpenJDK to support this.
- The original SEDRE application directory structure available, as the build script needs to reference its libraries for compilation.

### Building (\*nix)

1.  **Download the repository**

    ```bash
    git clone https://github.com/iwconfig/sedre-mods
    cd sedre-mods
    ```

    or

    ```bash
    wget https://github.com/iwconfig/sedre-mods/archive/refs/heads/main.tar.gz | tar vxf -
    # or curl -L https://github.com/iwconfig/sedre-mods/archive/refs/heads/main.tar.gz | tar vxf -
    cd main
    ```

2.  **Configure build.conf**

    ```bash
    nano build.conf
    ```

3.  **Run the build script**

    ```bash
    ./build.sh

    # or alternatively
    SEDRE_INSTALL_DIR=/some/other/path ./build.sh
    ```

    This will compile the sources and create the JAR files in the project's root directory.

### Build Instructions using Docker (Recommended)

If you don't have a local JDK 8 environment, you can use Docker to build the patches.

```bash
# Clone the repository first, then from inside the project directory, run:
docker run --rm -it -v "$(pwd)":/work -v /path/to/your/SEDRE/installation/dir:/SEDRE:ro openjdk:8-slim bash -c "cd /work && bash build.sh"
```

This command mounts your current project directory and the SEDRE installation dir into the container, runs the build script, and leaves the resulting JAR files in your local project directory. It requires you to have SEDRE already installed. If you don't have it on hand you can download a zip file of the build environment [here](https://gist.github.com/iwconfig/4893ddcc35bf5802a02a85b0eb85fbbe/raw/sedre-libs.zip), or check the [the instructions below](#or-alternatively-provide-the-build-env-by-downloading-a-zip-archive-of-the-sedre-libs).

#### One-shot docker run that does it all

```bash
docker run --rm -i -w /repo -v "$(pwd)":/jars -v /path/to/your/SEDRE/installation/dir:/SEDRE:ro openjdk:8-slim bash -s <<EOF
set -ve
# Installing wget.
apt update; apt install -y wget

# Downloading and unpacking repo.
wget -O- https://github.com/iwconfig/sedre-mods/archive/refs/heads/main.tar.gz | tar vxzf - --strip-components=1

# Starting the build process.
SEDRE_INSTALL_DIR=/SEDRE bash build.sh

# Moving jars to your bind mount.
mv -v *.jar /jars

# All done. Bon voyage!
EOF
```

##### or alternatively provide the build env by downloading a zip archive of the sedre libs

```bash
docker run --rm -i -w /repo -v "$(pwd)":/jars openjdk:8-slim bash -s <<EOF
set -ve
# Installing wget.
apt update; apt install -y wget

# Downloading and unpacking repo.
wget -O- https://github.com/iwconfig/sedre-mods/archive/refs/heads/main.tar.gz | tar vxzf - --strip-components=1

# Downloading and unpacking build environment.
wget -O- https://gist.github.com/iwconfig/4893ddcc35bf5802a02a85b0eb85fbbe/raw/sedre-libs.zip | { mkdir /build_env && cd \$_ && jar xv ;}

# Starting the build process.
SEDRE_INSTALL_DIR=/build_env bash build.sh

# Moving jars to your bind mount.
mv -v *.jar /jars

# All done. Bon voyage!
EOF
```

##### or to both build and install the jar files and the discs.properties.template into your SEDRE installation

```bash
docker run --rm -i -w /repo -v /path/to/your/SEDRE/installation/dir:/SEDRE openjdk:8-slim bash -s <<EOF
set -ve
# Installing wget.
apt update; apt install -y wget

# Downloading and unpacking repo.
wget -O- https://github.com/iwconfig/sedre-mods/archive/refs/heads/main.tar.gz | tar vxzf - --strip-components=1

# Starting the build process.
SEDRE_INSTALL_DIR=/SEDRE bash build.sh

# Installing jars and discs.properties.
mv -v *.jar /SEDRE/Resources/Sdr/WEB-INF/lib/
mv -v discs.properties.template /SEDRE/Properties/extconf/discs.properties

# All done. Remember to edit discs.properties. Bon voyage!
EOF
```

## How to Add New Patches

This build system is modular. To add a new patch:

1.  Place your new Java source file(s) in the `src/` directory, following the original package structure.
2.  Create a new `.conf` file inside the `build.conf.d/` directory.
3.  Define the `TARGET_JAR` and `JAVA_SOURCES` variables in your new config file, and configure `./build.conf` as needed.
4.  Run `build.sh` script. It will automatically find and build your new patch alongside the existing ones.
