# JPedal Microservice Example #

IDRsolutions' JPedal Microservice Example is an open source project that allows you to extract content from a PDF by running JPedal as a web service in the cloud or on-premise.

**Please note that JPedal is paid-for, commercial software - however you can use the trial version for free. Just rename the trial .jar to "jpedal.jar".**

-----

# How do I get set up? #

### What you'll need: ###

* [JPedal](https://www.idrsolutions.com/jpedal/download) - this provides all the conversion functionality.
* [Maven](https://maven.apache.org/download.cgi) - used to build the .war file.

### Trial Version: ###

If you do not have a full verison of JPedal you can get the trial version from [here](https://www.idrsolutions.com/jpedal/trial-download/).

### Accessing The Microservice: ###

The microservice can be accessed via a REST API, you can create your own client or API calls or use one of our premade examples in a collection of different languages.
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

See our [application server tutorials](https://docs.idrsolutions.com/jpedal/app-server-deployment/) for instructions on deployment.

See our [cloud platform tutorials](https://docs.idrsolutions.com/jpedal/docker-deployment/) for instructions on cloud deployment.

### docker-compose deployment: ###

We provide a docker image and information on how to deploy it [here](https://github.com/idrsolutions/jpedal-docker).


-----

## Usage: ##
You can interact with the JPedal Microservice Example using the REST API.
A complete list of raw requests that can be used can be found [here](/API.md).

The JPedal Microservice Example can perform several different conversion or extraction options.
Below are links to the documentation for each option available.

 - [Convert PDF pages to Images](https://docs.idrsolutions.com/jpedal/convert-pdf-to-image/) will convert each page into an image.
 - [Extract Images from PDF pages](https://docs.idrsolutions.com/jpedal/extract-images-from-pdf/) will extract each image found on a page.
 - [Extract Text from PDF pages](https://docs.idrsolutions.com/jpedal/extract-text-from-pdf/) will extract all the text from each page.

-----

# Who do I talk to? #

Found a bug, or have a suggestion / improvement? Let us know through the Issues page.

Got questions? You can contact us [here](https://idrsolutions.my.site.com/s/request).

-----

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

[http://www.apache.org/licenses/LICENSE-2.0](http://www.apache.org/licenses/LICENSE-2.0)

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
