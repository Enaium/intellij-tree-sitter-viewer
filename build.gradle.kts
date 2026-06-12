import org.gradle.kotlin.dsl.assign
import org.jetbrains.changelog.markdownToHTML
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformExtension

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.changelog)
    alias(libs.plugins.platform)
}

group = "cn.enaium.treesitter.viewer"
version = "1.0.0"

repositories {
    mavenCentral()
    intellijPlatform {
        releases()
        marketplace()
        defaultRepositories()
    }
}


dependencies {
    intellijPlatform {
        create(project.property("platformType").toString(), project.property("platformVersion").toString())
    }
    implementation(libs.tree.sitter)
    implementation(libs.tree.sitter.agda)
    implementation(libs.tree.sitter.bash)
    implementation(libs.tree.sitter.c)
    implementation(libs.tree.sitter.c.sharp)
    implementation(libs.tree.sitter.cpp)
    implementation(libs.tree.sitter.css)
    implementation(libs.tree.sitter.embedded.template)
    implementation(libs.tree.sitter.go)
    implementation(libs.tree.sitter.haskell)
    implementation(libs.tree.sitter.html)
    implementation(libs.tree.sitter.java)
    implementation(libs.tree.sitter.javascript)
    implementation(libs.tree.sitter.json)
    implementation(libs.tree.sitter.julia)
    implementation(libs.tree.sitter.kotlin)
    implementation(libs.tree.sitter.ocaml)
    implementation(libs.tree.sitter.php)
    implementation(libs.tree.sitter.python)
    implementation(libs.tree.sitter.regex)
    implementation(libs.tree.sitter.ruby)
    implementation(libs.tree.sitter.rust)
    implementation(libs.tree.sitter.scala)
    implementation(libs.tree.sitter.tsx)
    implementation(libs.tree.sitter.typescript)
    implementation(libs.tree.sitter.verilog)
    implementation(libs.tree.sitter.graphql)
    implementation(libs.tree.sitter.hack)
    implementation(libs.tree.sitter.hcl)
    implementation(libs.tree.sitter.hocon)
    implementation(libs.tree.sitter.jq)
    implementation(libs.tree.sitter.json5)
    implementation(libs.tree.sitter.lalrpop)
    implementation(libs.tree.sitter.latex)
    implementation(libs.tree.sitter.lean)
    implementation(libs.tree.sitter.llvm)
    implementation(libs.tree.sitter.llvm.mir)
    implementation(libs.tree.sitter.lua)
    implementation(libs.tree.sitter.m68k)
    implementation(libs.tree.sitter.make)
    implementation(libs.tree.sitter.markdown)
    implementation(libs.tree.sitter.meson)
    implementation(libs.tree.sitter.nginx)
    implementation(libs.tree.sitter.nim)
    implementation(libs.tree.sitter.nix)
    implementation(libs.tree.sitter.objc)
    implementation(libs.tree.sitter.ohm)
    implementation(libs.tree.sitter.org)
    implementation(libs.tree.sitter.p4)
    implementation(libs.tree.sitter.pascal)
    implementation(libs.tree.sitter.perl)
    implementation(libs.tree.sitter.pgn)
    implementation(libs.tree.sitter.pod)
    implementation(libs.tree.sitter.proto)
    implementation(libs.tree.sitter.qmljs)
    implementation(libs.tree.sitter.query)
    implementation(libs.tree.sitter.r)
    implementation(libs.tree.sitter.racket)
    implementation(libs.tree.sitter.rasi)
    implementation(libs.tree.sitter.re2c)
    implementation(libs.tree.sitter.rego)
    implementation(libs.tree.sitter.rst)
    implementation(libs.tree.sitter.scheme)
    implementation(libs.tree.sitter.scss)
    implementation(libs.tree.sitter.sexp)
    implementation(libs.tree.sitter.smali)
    implementation(libs.tree.sitter.sourcepawn)
    implementation(libs.tree.sitter.sparql)
    implementation(libs.tree.sitter.sql)
    implementation(libs.tree.sitter.sql.bigquery)
    implementation(libs.tree.sitter.sqlite)
    implementation(libs.tree.sitter.ssh.client.config)
    implementation(libs.tree.sitter.svelte)
    implementation(libs.tree.sitter.swift)
    implementation(libs.tree.sitter.tablegen)
    implementation(libs.tree.sitter.tact)
    implementation(libs.tree.sitter.thrift)
    implementation(libs.tree.sitter.toml)
    implementation(libs.tree.sitter.turtle)
    implementation(libs.tree.sitter.twig)
    implementation(libs.tree.sitter.vhdl)
    implementation(libs.tree.sitter.vue)
    implementation(libs.tree.sitter.wast)
    implementation(libs.tree.sitter.wat)
    implementation(libs.tree.sitter.wgsl)
    implementation(libs.tree.sitter.yaml)
    implementation(libs.tree.sitter.yang)
    implementation(libs.tree.sitter.zig)
}

tasks {
    patchPluginXml {
        sinceBuild.set(project.property("sinceBuild").toString())
        untilBuild.set(project.property("untilBuild").toString())
    }
}

configure<IntelliJPlatformExtension> {
    configure<IntelliJPlatformExtension.PluginConfiguration> {
        description = markdownToHTML(rootProject.file("description.md").readText())
        changeNotes = markdownToHTML(rootProject.file("changelog.md").readText())
    }
}

kotlin {
    jvmToolchain(17)
}