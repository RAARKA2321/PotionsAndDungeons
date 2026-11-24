package main

import (
	"os"
	"os/exec"
	"path/filepath"
	"regexp"
	"strconv"
	"strings"
	"syscall"
	"unsafe"
)

var version = "dev"
var releaseVersion = regexp.MustCompile(`^[0-9]{1,6}\.[0-9]{1,6}\.[0-9]{1,6}$`)

type gameBuild struct {
	dir     string
	version string
}

func main() {
	executable, err := os.Executable()
	if err == nil {
		root := filepath.Dir(executable)
		err = runInstalledGame(root)
	}
	if err != nil {
		title, _ := syscall.UTF16PtrFromString("Potions and Dungeons")
		message, _ := syscall.UTF16PtrFromString("Не удалось запустить игру. Распакуй ZIP целиком.\n\n" + err.Error())
		syscall.NewLazyDLL("user32.dll").NewProc("MessageBoxW").Call(
			0, uintptr(unsafe.Pointer(message)), uintptr(unsafe.Pointer(title)), 0x10)
	}
}

func runInstalledGame(root string) error {
	base := gameBuild{root, version}
	restartFile := filepath.Join(root, ".updates", "restart")
	build := latestBuild(base)
	for {
		game := exec.Command(filepath.Join(build.dir, "runtime", "bin", "javaw.exe"),
			"-Dpotions.version="+build.version, "-Dpotions.launcher.dir="+root,
			"-cp", filepath.Join(build.dir, "lib", "*"),
			"com.example.potionsanddungeons.DesktopPlatformKt")
		game.Dir = root
		err := game.Run()
		if err != nil && build.dir != root {
			// Retain the original installation and exclude a build that failed to launch.
			_ = os.WriteFile(filepath.Join(build.dir, "failed"), nil, 0600)
			fallback := latestBuild(base)
			if fallback.dir == build.dir {
				fallback = base
			}
			build = fallback
			continue
		}
		requested, readErr := os.ReadFile(restartFile)
		if readErr == nil {
			_ = os.Remove(restartFile)
		}
		if err != nil || readErr != nil {
			return err
		}
		next := latestBuild(base)
		if string(requested) != next.version || !newerVersion(next.version, build.version) {
			return nil
		}
		build = next
	}
}

func latestBuild(base gameBuild) gameBuild {
	updates := filepath.Join(base.dir, ".updates")
	entries, err := os.ReadDir(updates)
	if err != nil {
		return base
	}
	latest := base
	for _, entry := range entries {
		tag := entry.Name()
		if !entry.IsDir() || !newerVersion(tag, latest.version) {
			continue
		}
		dir := filepath.Join(updates, tag)
		if _, err := os.Stat(filepath.Join(dir, "failed")); !os.IsNotExist(err) {
			continue
		}
		ready, err := os.ReadFile(filepath.Join(dir, "ready"))
		if err != nil || string(ready) != tag ||
			!regularFile(filepath.Join(dir, "runtime", "bin", "javaw.exe")) ||
			!regularFile(filepath.Join(dir, "lib", "PotionsAndDungeons-"+tag+".jar")) {
			continue
		}
		latest = gameBuild{dir, tag}
	}
	return latest
}

func regularFile(path string) bool {
	info, err := os.Stat(path)
	return err == nil && info.Mode().IsRegular()
}

func newerVersion(candidate string, current string) bool {
	if !releaseVersion.MatchString(candidate) || !releaseVersion.MatchString(current) {
		return false
	}
	left, right := strings.Split(candidate, "."), strings.Split(current, ".")
	for i := range left {
		a, _ := strconv.Atoi(left[i])
		b, _ := strconv.Atoi(right[i])
		if a != b {
			return a > b
		}
	}
	return false
}
