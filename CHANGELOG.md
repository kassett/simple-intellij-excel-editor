# intellij-excel-editor

## 0.2.0

### Minor Changes

- baa27be: Add simple `.xlsx` cell editing with dirty-state tracking and an explicit save action in the Excel editor.
- 77ea703: Add the initial IntelliJ Excel editor plugin scaffold, read-only workbook viewer, Nix development environment, and CI/release automation.
- 4971446: Move save controls to the top right, add restore support, and expose row and column insertion/deletion from row-number and column-header context menus.

### Patch Changes

- df8d241: Replace the native Swing workbook table with a JCEF-hosted AG Grid editor shell.
- bfa53cd: Implement the required `FileEditor.getFile()` override so the Excel editor works cleanly in current IntelliJ Platform builds.
