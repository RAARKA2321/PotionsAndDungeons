package main

import (
	"os"
	"path/filepath"
	"testing"
)

func TestLatestBuildChoosesOnlyReadyNewerVersions(t *testing.T) {
	root := t.TempDir()
	base := gameBuild{root, "1.9.0"}
	add := func(version, marker string) string {
		t.Helper()
		dir := filepath.Join(root, ".updates", version)
		files := map[string]string{
			"runtime/bin/javaw.exe":                      "",
			"lib/PotionsAndDungeons-" + version + ".jar": "",
			marker: version,
		}
		for relative, data := range files {
			path := filepath.Join(dir, relative)
			if err := os.MkdirAll(filepath.Dir(path), 0700); err != nil {
				t.Fatal(err)
			}
			if err := os.WriteFile(path, []byte(data), 0600); err != nil {
				t.Fatal(err)
			}
		}
		return dir
	}
	add("1.8.9", "ready")
	ready := add("1.10.1", "ready")
	add("1.10.2", "partial")
	failed := add("1.11.0", "ready")
	if err := os.WriteFile(filepath.Join(failed, "failed"), nil, 0600); err != nil {
		t.Fatal(err)
	}
	if got := latestBuild(base); got.dir != ready || got.version != "1.10.1" {
		t.Fatalf("selected %+v, want 1.10.1", got)
	}
}
