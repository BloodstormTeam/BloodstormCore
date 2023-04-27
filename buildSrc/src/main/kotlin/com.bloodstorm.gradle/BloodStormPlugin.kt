package com.bloodstorm.gradle

import net.md_5.specialsource.JarMapping
import net.md_5.specialsource.JarRemapper
import net.md_5.specialsource.provider.ClassLoaderProvider
import net.md_5.specialsource.provider.JointProvider
import org.apache.commons.io.FileUtils
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileVisitDetails
import org.gradle.api.file.FileVisitor
import org.gradle.api.tasks.compile.JavaCompile
import org.ultramine.gradle.internal.DirectoryClassRepo
import org.ultramine.gradle.internal.RepoInheritanceProvider
import java.io.File
import java.io.FileFilter
import java.net.URL
import java.net.URLClassLoader

class BloodStormPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.tasks.register("buildServer") { task ->
            task.dependsOn("build")
            val javaCompile: JavaCompile = target.tasks.getByPath("compileJava") as JavaCompile
            val outputDir = File(target.buildDir, "server").also { it.mkdir() }
            println("Perform obfuscation...")
            val mappingDirectory = File(target.projectDir, "mappings")
            if (!mappingDirectory.isDirectory) mappingDirectory.mkdir()
            val mappingFiles = mappingDirectory.listFiles(FileFilter { it.name.endsWith(".srg") })
            if (mappingFiles != null) {
                processObfuscation(target, mappingFiles.toList(), javaCompile.classpath, javaCompile.destinationDirectory.asFile.orNull!!, outputDir)
            }
            println("Obfuscation successfully!")
        }
    }

    private fun processObfuscation(project: Project, srgs: List<File>, classpath: FileCollection, destinationDir: File, outputDir: File) {
        val mapping = JarMapping()
        srgs.forEach { mapping.loadMappings(it) }
        val classRepo = DirectoryClassRepo(destinationDir)
        val inheritanceProviders = JointProvider()
        inheritanceProviders.add(RepoInheritanceProvider(classRepo))
        inheritanceProviders.add(ClassLoaderProvider(URLClassLoader(classpath.files.toUrls())))
        mapping.setFallbackInheritanceProvider(inheritanceProviders)
        val remapper = JarRemapper(null, mapping)
        FileUtils.cleanDirectory(outputDir)
        project.fileTree(destinationDir).visit(object : FileVisitor {
            override fun visitDir(dirDetails: FileVisitDetails) {}
            override fun visitFile(fileDetails: FileVisitDetails) {
                val bytes = remapper.remapClassFile(FileUtils.readFileToByteArray(File(destinationDir, fileDetails.path)), classRepo)
                FileUtils.writeByteArrayToFile(File(outputDir, remapper.map(fileDetails.path.substring(0, fileDetails.path.length - 6) + ".class")), bytes)
            }
        })
    }

    private fun Collection<File>.toUrls(): Array<URL> {
        val result = mutableListOf<URL>()
        this.forEach {
            result.add(it.toURI().toURL())
        }
        return result.toTypedArray()
    }
}