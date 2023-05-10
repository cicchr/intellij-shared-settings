# Shared Settings IntelliJ Plugin

IntelliJ has builtin support for an individual user to synchronize settings, but this is only effective for individuals
and teams. Additionally, the Settings Repository plugin exists, but it overwrites all settings including personalization 
settings. This plugin aims to allow teams to synchronize select settings between team members without overwriting user
specific preferences such as themes and keymaps. For the moment this supports Code Style and Inspections.

### How share a profile

This plugin uses a git repository to facilitate the synchronization among team members. To prevent accidental overwriting
of the shared configuration the plugin will only ever read from the repository. Updates to the shared settings must be
done manually.

First configure a Code Style or Inspections profile for sharing in IntelliJ. Once configured export the profile (Code
Style should be exported as `IntelliJ IDEA code style XML`). Place the exported Code Style xml in a folder named
`codestyles` within your git repository. Place the exported Inspections profile in a folder name `inspection` within
your git repository. Once the files are committed anyone can use the Shared Settings plugin to keep a local profile up
to date. Note: the synced profile names are made to match the name of the file in the git repository.

### How to enforce use of a profile

Some teams way want the ability to ensure IntelliJ is using the correct profile. To do this another file can be added to
the git repository to automatically set the profile to the chosen one. To do this, create an `enforced.properties` file
in the root of your git repository. You can then specify the name of the Inspection and/or Code Style profile you want
to enforce. Note: The name of the profile should match the name of the xml file in your git repository. The file will
look like this:

```properties
inspection=MyInspectionProfile
codestyles=MyCodeStyleProfile
```

### How to configure the plugin

Once the git repository is set up, you can start using the plugin to synchronize settings from the repository. First,
configure the plugin by going to `Settings > Tools > Shared Settings`, and paste the URL to your git repository. Then
click `Get Branches` to list the branches in your repository, and select the branch you want to use. When you click
apply the plugin will automatically create new profiles from the git repository and switch every project to use them if
it is configured to do so (you can turn off enforcement in the plugin settings). If enforcement is enabled, whenever a
project is opened the plugin will attempt to resynchronize from the git repository. It will also prevent modification of
the imported profiles and ensure they remain selected. You can also manually synchronize from the repository by going to
`Tools > Sync and Enforce Shared Settings`