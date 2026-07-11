{
  description = "IntelliJ Excel editor plugin";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    flake-utils.url = "github:numtide/flake-utils";
  };

  outputs = { nixpkgs, flake-utils, ... }:
    flake-utils.lib.eachDefaultSystem (system:
      let
        pkgs = import nixpkgs { inherit system; };
      in
      {
        devShells.default = pkgs.mkShell {
          packages = with pkgs; [
            curl
            git
            gradle
            jq
            jdk21
            nodejs_22
            pnpm
          ];

          JAVA_HOME = "${pkgs.jdk21}";
        };
      });
}
