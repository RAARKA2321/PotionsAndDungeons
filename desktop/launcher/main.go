package main

import (
	"os"
	"os/exec"
	"path/filepath"
	"syscall"
	"unsafe"
)

var version = "dev"

func main() {
	executable, err := os.Executable()
	if err == nil {
		root := filepath.Dir(executable)
		game := exec.Command(filepath.Join(root, "runtime", "bin", "javaw.exe"),
			"-Dpotions.version="+version, "-cp", filepath.Join(root, "lib", "*"),
			"com.example.potionsanddungeons.DesktopPlatformKt")
		game.Dir = root
		err = game.Run()
	}
	if err != nil {
		title, _ := syscall.UTF16PtrFromString("Potions and Dungeons")
		message, _ := syscall.UTF16PtrFromString("Не удалось запустить игру. Распакуй ZIP целиком.\n\n" + err.Error())
		syscall.NewLazyDLL("user32.dll").NewProc("MessageBoxW").Call(
			0, uintptr(unsafe.Pointer(message)), uintptr(unsafe.Pointer(title)), 0x10)
	}
}
