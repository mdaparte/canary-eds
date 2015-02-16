(Cloned from the National Sandia Laboratories [SVN repository](https://software.sandia.gov/trac/canaryeds#WelcometotheTracSiteforCanaryEDS))

---

# CanaryEDS 

CanaryEDS is a Java library providing the core functionality that the CANARY-EDS software includes. The CanaryEDS Library is a library; the stand-alone program package that is included in the source tree is not as fully functional as the CANARY-EDS executable. This library is primarily intended for 3rd-party developers to use in their software, and for open-source enhancements to be incorporated (generally in the form of new algorithms). 

The following directory structure exists for the CANARYEDS repository

### Files

 * `pom.xml`         - Contains the parent POM for CANARYEDS modules
 * `pom-modules.xml` - Contains a POM to build different modules for unified Javadocs

### Directories/Modules

 * `canary-core-old/`     - Contains the v4 `CanarysCore.jar` source code
 * `canaryeds-base/`      - Contains the base CANARYEDS library functionality
 * `canaryeds-program/`   - Contains the GUI and command line executable files module
 * `canaryeds-external*/` - Contains specific drivers for different middleware (future work)
 * `canaryeds-builder/`   - Contains a configuration file builder program
 * `canaryeds-pom/`       - Contains only the parent POM, for continuous integration builder
 * `examples/`            - Contains example configuration and data files for CANARYEDS 5

To build CANARYEDS:

1. Build v1.0 of the [Seme Framework](https://github.com/willfurnass/seme), or install the Jar for `seme-framework-1.0.jar` into your local maven repo

    ``` bash
        cd seme/seme-framework 
        mvn clean compile install
    ```

2. Install the `org.canaryeds::gov-sandia-canaryeds` parent POM in your local maven repo

    ``` bash
        cd canaryeds-pom
        mvn install
    ```

3. Build the `canaryeds-base` module to get the library functions

    ``` bash
        cd canaryeds-base
        mvn clean compile install
    ```

Doing the three above will give access to the following packages:

 * `gov.sandia.seme.framework`
 * `gov.sandia.seme.util`
 * `org.canaryeds.base`
 * `org.canaryeds.base.*`

The `canaryeds-program` module has the following dependencies:

 * `seme-framework-1.0`
 * `canaryeds-base`
 * `canary-core-old`
 * `canaryeds-external-eddies` (empty jar at this time)

The `canaryeds-program/src/main/java/gov/sandia/canaryeds/program/CanaryEDS.java` file contains the commandline routine which shows how to use CANARYEDS as a library, assuming that the routine calling CANARY does not exit from `main`.

The `seme-framework-1.0` tests _should_ all be passing.
