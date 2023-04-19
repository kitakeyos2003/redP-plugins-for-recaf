## Plugin Rename Package for Recaf

This plugin for [Recaf](https://github.com/Col-E/Recaf) allows you to easily rename the default package of a Java project.

### Installation

Download the latest release of the plugin from the [Releases](https://github.com/kitakeyos2003/redP-plugins-for-recaf/releases) page.
Copy the downloaded JAR file into the **\`plugins\`** directory of your Recaf installation.
Usage

1. Navigate to the `plugins` folder.
    - Windows: `%APPDATA%/Recaf/plugins`
	- Linux: `$HOME/Recaf/plugins`
2. Copy plugin jar into this folder
3. Run Recaf to verify your plugin loads.

Open the Java project you want to modify in Recaf.
Go to the **\`Plugins\`** menu and select **\`Rename Package\`**.
Enter the new package name in the dialog box.
Click **\`OK\`** to apply the changes.
Note that this plugin only modifies the package name in the bytecode. If your project has any external dependencies that reference the old package name, you will need to update them manually.

### Contributing

If you find any issues with this plugin, feel free to open an issue on the GitHub repository. Pull requests are also welcome.

### License

This plugin is licensed under the MIT License.
