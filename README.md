# Intyg Gradle Plugin
Gradle plugin för att användas av de övriga intygsprojekten under [SKL Intyg](http://github.com/sklintyg) vid byggen.


## Dokumenatation

##### Uppdatera license-headers i källkodsfiler 

    $ gradle licenseFormat -PcodeQuality
    
##### Publisera till release repo

    $ gradle publish
    
För att kunna publisera till release repot behöver användarnamn och lösenord först sättas
via systemvariablerna **nexusUsername** och **nexusPassword**        

##### Publisera till lokalt repo:

    $ gradle publishToMavenLocal    

Men innan publisering till lokalt repo behöver några justering göras:

I **build.gradle.kt** behöver man ändra versionsnummer
 
    version = System.getProperty("buildVersion") ?: "1.1-SNAPSHOT"

 Observera att det står 1.1-SNAPSHOT men av någon anledning går det inta att publisera snapshot för en plugin.
 Så använd "1.1" istället för "1.1-SNAPSHOT"

Sedan behöver rätt version sättas i projektets root **build.gradle** fil så att önskad plugin-version används. 

    plugins {
        //id "se.inera.intyg.plugin.common" version "1.0.63" apply false
        id "se.inera.intyg.plugin.common" version "1.1" apply false
        id "org.akhikhl.gretty" version "1.4.2" apply false
        id "com.moowork.node" version "1.1.1" apply false
    } 

Och för att Gradle ska hitta vår plugin i vårt lokala repo behövs också kommenteringen för _mavenLocal()_ tas bort
i projektets **settings.gradle** file.

    pluginManagement {
        repositories {
            // Uncomment line below during development of gradle-intyg-plugin
            //mavenLocal()
            maven { url "https://build-inera.nordicmedtest.se/nexus/repository/releases/" }
            gradlePluginPortal()
        }
    }

## Licens
Copyright (C) 2016 Inera AB (http://www.inera.se)

Intyg Gradle Plugin is free software: you can redistribute it and/or modify it under the terms of the GNU LESSER GENERAL PUBLIC LICENSE as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

Intyg Gradle Plugin is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU LESSER GENERAL PUBLIC LICENSE for more details.

Se även [LICENSE.md](https://github.com/sklintyg/gradle-intyg-plugin/blob/master/LICENSE.md).
