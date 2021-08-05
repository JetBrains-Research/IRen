# IRen

## Description

This plugin makes variable name suggestions for you.

## Installation

1. In Intellij IDEA add custom plugin
   repository `https://buildserver.labs.intellij.net/guestAuth/repository/download/ijplatform_IntelliJProjectDependencies_CodeCompletionProjects_IdentifiersNamesSuggesting_BuildPlugin/lastSuccessful/updatePlugins.xml`. [Instruction](https://www.jetbrains.com/help/idea/managing-plugins.html#repos)
2. Install plugin `Id Names Suggesting` in Marketplace.

## Usage

- Go to `Analyze/Train Project Id Model` to train model on an opened project.

- When a caret stands on a variable click `Alt+Enter` and choose `Suggest variable name`. 
Plugin will show you a list of consistent names for this variable.

## Structure

### Modules:

- `plugin` module contains plugin itself
- `experiments` module contains all experiments that were made

### Directories of `plugin`:

- `org.jetbrains.id.names.suggesting` – part of a plugin that predicts variable names. Done by Igor Davidenko.
- `org.jetbrains.astrid` – part of a plugin that predicts method names. Done by Zarina Kurbatova.

