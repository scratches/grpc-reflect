with import <nixpkgs> { };

mkShell {

  name = "env";
  buildInputs = [
    grpcurl
    protobuf
    python3Packages.python
    python3Packages.ipykernel
    python3Packages.venvShellHook
  ];

  shellHook = ''
  '';

  venvDir = "./.venv";
  postVenvCreation = ''
    unset SOURCE_DATE_EPOCH
    pip install --prefix=.venv ipython ipykernel jupyter
    pip install --prefix=.venv jbang
  '';

  postShellHook = ''
    # allow pip to install wheels
    unset SOURCE_DATE_EPOCH
    export LD_LIBRARY_PATH="${pkgs.stdenv.cc.cc.lib.outPath}/lib:$LD_LIBRARY_PATH";
  '';

}