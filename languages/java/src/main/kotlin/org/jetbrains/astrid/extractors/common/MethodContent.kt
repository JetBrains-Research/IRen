package org.jetbrains.astrid.extractors.common

import com.github.javaparser.ast.Node

class MethodContent(val leaves: ArrayList<Node>, val name: String, val length: Long)