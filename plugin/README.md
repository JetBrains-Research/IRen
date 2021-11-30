<!-- Plugin description -->
Provides assistance in variable refactoring.

This tool replaces default IDE refactoring with the new one
that will recommend you better variable names with the help of Machine Learning methods.

To suggest consistent names IRen model has to train on a project in which you are currently working.
By the default IRen plugin automatically trains/loads model on a startup of the opened project.
If you want to manually control it you can switch off the corresponding option in the settings
**Tools | IRen** and launch training by yourself clicking **Refactor | Train IRen Model**.
Also in the settings you can tune some hyperparameters of the model.

IRen inspection helps with maintenance of the code and marks all variables which names are not good enough.

### Supported languages:
- Java
- Kotlin
- Python
<!-- Plugin description end -->
