# TextExplorer

This is a tool designed for the exploration and comparison of variants of textual data.

## Getting Started

### Initial Setup

1. **Download and Install**
   - Get the [latest release](https://github.com/Paulanerus/TextExplorer/releases/latest) of the application and install it on your system

2. **Obtain a Plugin and Dataset**
   - Download a demo plugin from the release page along with its [corresponding dataset](https://zenodo.org/records/12723324), or create your own

3. **Load the Plugin**
   - Start the application
   - Load the plugin via the menu in the top right-corner of the window
   - Depending on the size of your dataset, the loading process may take some time

### Search

1. **Select a Datapool**
   - If multiple datasets are available, you can switch between them using the menu in the top-left corner.

2. **Perform a Search**
   - Enter simple words or sentences to search through the dataset.
   - Use Boolean operators to refine your search: and, and not, or

3. Use Name Variants
   - The syntax for searching name variants is: `@name:value`

4. Apply Pre-Filters
   - You can pre-filter search results using the following syntax: `@name:value1:value2`

*The 'name' for name variants and pre-filters corresponds to the value specified in @DataSource and is visible in the Info View.*

## Development

To create plugins for TextExplorer, see the [API ReadMe](api/README.md) for details.

## Support

If you have any problems or questions about this project, please get in touch. You can
also [open an issue](https://github.com/Paulanerus/TextExplorer/issues) on GitHub.