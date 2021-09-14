# IRen

[![jetbrains plugins](https://img.shields.io/jetbrains/plugin/v/15432-iren.svg)](https://plugins.jetbrains.com/plugin/15432-iren)
![TeamCity build status](https://buildserver.labs.intellij.net/app/rest/builds/buildType:id:ijplatform_IntelliJProjectDependencies_CodeCompletionProjects_SuggestingIdentifierNames_Publish/statusIcon.svg)
## Description

This plugin makes variable name suggestions for you.

## Installation

1. [Download](https://plugins.jetbrains.com/plugin/15432-iren/versions) latest version
2. Install it manually `Preferences` > `Plugins` > `Install plugin from disk...`

## Features

This tool replaces default IDE refactoring with the new one
that will recommend You better variable names with the help of Machine Learning methods.

To suggest consistent names IRen model has to train on a project in which you are currently working.
By the default IRen plugin automatically trains/loads model on a startup of the opened project but
if you want to manually control it you can switch off the corresponding option in the settings
**Tools | IRen settings** and do it by yourself launching **Refactor | Train IRen Model**.
Also in the settings you can tune some hyperparameters of the model.

IRen inspection helps with maintenance of the code and marks all variables which names are not good enough.

## Structure

### Modules:

- `plugin` module contains plugin itself
- `experiments` module contains all experiments that were made

### Directories of `plugin`:

- `org.jetbrains.id.names.suggesting` – part of a plugin that predicts variable names. Done by Igor Davidenko.
- `org.jetbrains.astrid` – part of a plugin that predicts method names. Done by Zarina Kurbatova.

