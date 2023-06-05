# This flake is heavily indebted to the flake template in the sbt-derivation project:
# https://github.com/zaninime/sbt-derivation/blob/92d6d6d825e3f6ae5642d1cce8ff571c3368aaf7/templates/cli-app/flake.nix
#
# TODO(jb):
# sbt override to select proper jdk (to fix the jdk!)
{
  description = "Scala fullstack template flake";

  # Flake inputs
  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs";
    sbt-derivation = {
      url = "github:zaninime/sbt-derivation";
      inputs.nixpkgs.follows = "nixpkgs";
    };
    flake-utils.url = "github:numtide/flake-utils";
  };

  # Flake outputs
  outputs = { self, nixpkgs, sbt-derivation, flake-utils, ... }:
    flake-utils.lib.eachDefaultSystem (system:
      let
        pkgs = import nixpkgs { inherit system; };

        name = "template-scala-fullstack";
        version = "0.1.0";
        mainClass = "Server";

        backend = sbt-derivation.mkSbtDerivation.${system} {
          pname = name;
          inherit version;

          # If you see an error like:
          #
          #     error: hash mismatch in fixed-output derivation:
          #         specified: sha256-rFh3dTcK65/sFOy2mQI6HxK+VQdzn3XvBNaVksSvP0U=
          #         got:       sha256-RDGw/rpKgMYdcYzWRITQLbBYDau4Xmm5ZPN5yRzQoaI=
          #
          # Then the hash from the 'got' section needs to go here:
          depsSha256 = "sha256-RDGw/rpKgMYdcYzWRITQLbBYDau4Xmm5ZPN5yRzQoaI=";

          src = ./.;
          depsWarmupCommand = ''
            sbt 'managedClasspath; compilers'
          '';
          startScript = ''
            #!${pkgs.runtimeShell}
            exec ${pkgs.openjdk_headless}/bin/java ''${JAVA_OPTS:-} -cp "${
              placeholder "out"
            }/share/${name}/lib/*" ${nixpkgs.lib.escapeShellArg mainClass} "$@"
          '';
          buildPhase = ''
            sbt stage
          '';
          # Note: The 'backend' in `cp` might need to be abstracted?
          installPhase = ''
            libs_dir="$out/share/${name}/lib"
            mkdir -p "$libs_dir"
            cp -ar backend/target/universal/stage/lib/. "$libs_dir"
            install -T -D -m755 $startScriptPath $out/bin/${name}
          '';
          passAsFile = [ "startScript" ];
        };
      in
      {
        packages.backend = backend;
        packages.default = backend;

        devShells.default =
          pkgs.mkShell {
            buildInputs = with pkgs; [
              # general
              bashInteractive
              git
              nixpkgs-fmt

              # scala
              sbt

              # javascript
              nodejs-18_x
              yarn
            ];
            # TODO: might need to re-evaluate in `.envrc`
            shellHook = ''
                # load environment variables
                set -a; source .env; set +a

                export PS1='\e[1;32m[nix-dev:\w]\$\e[0m '
            '';
          };
      });
}
