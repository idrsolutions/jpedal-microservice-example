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

Tutorials for application servers and cloud platforms coming soon...

-----

## Usage: ##

You can interact with the JPedal Microservice Example using the REST API.
A complete list of raw requests that can be used can be found [here](/API.md).

In order to perform an extraction you need to specify what you wish to extract using the settings value.
The following operations are currently available using the specified values.

## Convert Pages To Images ##
Convert each page of the document to an image at a given scaling (where 1.0 is 100%) in the specified image format.
### Settings ###
*Required*
**mode** : convertToImages
**format** : [png|bmp|tif|jpg|jpx]

*Optional*
**scaling** : 1.0

*Example*
mode:convertToImages;format:png;scalinh:2.0

### Extract Text ###
Extract the text from each page of a document. If the document contains [structured content](https://support.idrsolutions.com/hc/en-us/articles/360030091571) then this is extracted. If the content is unstructured the text is extracted by working down the page from left to right.
### Settings ###
*Required*
**mode** : extractText

*Example*
mode:extractText

### Extract Word List ###
Extract the text from each page of a document as a list of words including the page coordinates for each word. The coordinates are in the order of Left, Top, Right, Bottom.
### Settings ###
*Required*
**mode** : extractWordlist

*Example*
mode:extractWordlist

-----

# Who do I talk to? #

Found a bug, or have a suggestion / improvement? Let us know through the Issues page.

Got questions? You can contact us [here](https://idrsolutions.zendesk.com/hc/en-us/requests/new).

-----

Copyright 2020 IDRsolutions

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

[http://www.apache.org/licenses/LICENSE-2.0](http://www.apache.org/licenses/LICENSE-2.0)

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
