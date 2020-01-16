# JPedal Microservice Example #

IDRsolutions' JPedal Microservice Example is an open source project that allows you to convert PDF to HTML5 or SVG by running JPedal as a web service in the cloud or on-premise. 

**Please note that JPedal is paid-for, commercial software - however you can use the trial version for free. Just rename the trial .jar to "jpedal.jar".**

-----

# How do I get set up? #

### What you'll need: ###

* [JPedal](https://www.idrsolutions.com/jpedal/download) - this provides all the conversion functionality.
* [Maven](https://maven.apache.org/download.cgi) - used to build the .war file.

### Trial Version: ###

If you do not have a full verison of JPedal you can get the trial version from [here](https://www.idrsolutions.com/jpedal/trial-download/). 

### Accessing The Microservice: ###

The microservice can be access via a REST API, you can create your own client or API calls or use one of our premade examples in a collection of different languages.
These clients can be found at the following link.
https://github.com/idrsolutions/

### Build: ###

Clone a copy of the jpedal-microservice-example git repo:

```
git clone git://github.com/idrsolutions/jpedal-microservice-example
```

Add the JPedal jar to the project by copying it into the /lib directory. Make sure that it is named "jpedal.jar"

Open up a terminal / command prompt window in the base directory of the jpedal-microservice-example project, and build the .war file by running the command:
```
mvn compile war:war
```

This will generate the jpedal.war file inside the /target directory.

### Deployment: ###

See our [application server tutorials](https://support.idrsolutions.com/hc/en-us/sections/360000444652-Deploy-BuildVu-to-an-app-server) for instructions on deployment.

Tutorials for cloud platforms coming soon... 

### docker-compose deployment: ###

```
docker-compose up
```

Visiting `localhost:8080` will bring you to the Tomcat page. 
Click "Manager App" and log in with the following credentials: User - admin, Password - admin123.
These credentials can be changed in ./lib/tomcat-users.xml in this repository prior to starting the container.

In the list of applications, you will see the "/microservice-example" row at the bottom. In the "Commands" column, click "Start" if it is not already selected.

Upon first conversion, the output may not be visible. To fix this, stop the container and restart it again using `docker-compose up` as described previously.

-----

### Usage: ###

You can interact with the JPedal Microservice Example using the REST API.

A complete list of raw requests that can be used can be found [here](/API.md).

For specific languages, see our tutorials on how to [run JPedal from other languages](https://support.idrsolutions.com/hc/en-us/sections/360000451031-Run-JPedal-from-other-languages).

-----

# Who do I talk to? #

Found a bug, or have a suggestion / improvement? Let us know through the Issues page.

Got questions? You can contact us [here](https://idrsolutions.zendesk.com/hc/en-us/requests/new).

-----

Copyright 2018 IDRsolutions

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

[http://www.apache.org/licenses/LICENSE-2.0](http://www.apache.org/licenses/LICENSE-2.0)

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
