plugins { application }

dependencies { implementation(project(":core")) }

application { mainClass.set("javaboy.desktop.DesktopMain") }
