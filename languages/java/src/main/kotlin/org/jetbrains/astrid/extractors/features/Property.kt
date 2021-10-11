package org.jetbrains.astrid.extractors.features

import com.github.javaparser.ast.Node
import com.github.javaparser.ast.expr.AssignExpr
import com.github.javaparser.ast.expr.BinaryExpr
import com.github.javaparser.ast.expr.IntegerLiteralExpr
import com.github.javaparser.ast.expr.UnaryExpr
import com.github.javaparser.ast.type.ClassOrInterfaceType
import org.jetbrains.astrid.extractors.common.Common

class Property(node: Node, isLeaf: Boolean, isGenericParent: Boolean) {
    var rawType: String = ""
    var type: String = ""
    private var splitName: String = ""
    private var operator: String = ""
    private val numericalValues: List<String> = listOf("0", "1", "32", "64")
    private val primitiveType = "PrimitiveType"
    private val genericClassType = "GenericClass"
    private val num = "<NUM>"
    private val shortTypes = hashMapOf<String, String>(
            "ArrayAccessExpr" to "ArAc",
            "ArrayBracketPair" to "ArBr",
            "ArrayCreationExpr" to "ArCr",
            "ArrayCreationLevel" to "ArCrLvl",
            "ArrayInitializerExpr" to "ArIn",
            "ArrayType" to "ArTy",
            "AssertStmt" to "Asrt",
            "AssignExpr:and" to "AsAn",
            "AssignExpr:assign" to "As",
            "AssignExpr:lShift" to "AsLS",
            "AssignExpr:minus" to "AsMi",
            "AssignExpr:or" to "AsOr",
            "AssignExpr:plus" to "AsP",
            "AssignExpr:rem" to "AsRe",
            "AssignExpr:rSignedShift" to "AsRSS",
            "AssignExpr:rUnsignedShift" to "AsRUS",
            "AssignExpr:slash" to "AsSl",
            "AssignExpr:star" to "AsSt",
            "AssignExpr:xor" to "AsX",
            "BinaryExpr:and" to "And",
            "BinaryExpr:binAnd" to "BinAnd",
            "BinaryExpr:binOr" to "BinOr",
            "BinaryExpr:divide" to "Div",
            "BinaryExpr:equals" to "Eq",
            "BinaryExpr:greater" to "Gt",
            "BinaryExpr:greaterEquals" to "Geq",
            "BinaryExpr:less" to "Ls",
            "BinaryExpr:lessEquals" to "Leq",
            "BinaryExpr:lShift" to "LS",
            "BinaryExpr:minus" to "Minus",
            "BinaryExpr:notEquals" to "Neq",
            "BinaryExpr:or" to "Or",
            "BinaryExpr:plus" to "Plus",
            "BinaryExpr:remainder" to "Mod",
            "BinaryExpr:rSignedShift" to "RSS",
            "BinaryExpr:rUnsignedShift" to "RUS",
            "BinaryExpr:times" to "Mul",
            "BinaryExpr:xor" to "Xor",
            "BlockStmt" to "Bk",
            "BooleanLiteralExpr" to "BoolEx",
            "CastExpr" to "Cast",
            "CatchClause" to "Catch",
            "CharLiteralExpr" to "CharEx",
            "ClassExpr" to "ClsEx",
            "ClassOrInterfaceDeclaration" to "ClsD",
            "ClassOrInterfaceType" to "Cls",
            "ConditionalExpr" to "Cond",
            "ConstructorDeclaration" to "Ctor",
            "DoStmt" to "Do",
            "DoubleLiteralExpr" to "Dbl",
            "EmptyMemberDeclaration" to "Emp",
            "EnclosedExpr" to "Enc",
            "ExplicitConstructorInvocationStmt" to "ExpCtor",
            "ExpressionStmt" to "Ex",
            "FieldAccessExpr" to "Fld",
            "FieldDeclaration" to "FldDec",
            "ForeachStmt" to "Foreach",
            "ForStmt" to "For",
            "IfStmt" to "If",
            "InitializerDeclaration" to "Init",
            "InstanceOfExpr" to "InstanceOf",
            "IntegerLiteralExpr" to "IntEx",
            "IntegerLiteralMinValueExpr" to "IntMinEx",
            "LabeledStmt" to "Labeled",
            "LambdaExpr" to "Lambda",
            "LongLiteralExpr" to "LongEx",
            "MarkerAnnotationExpr" to "MarkerExpr",
            "MemberValuePair" to "Mvp",
            "MethodCallExpr" to "Cal",
            "MethodDeclaration" to "Mth",
            "MethodReferenceExpr" to "MethRef",
            "NameExpr" to "Nm",
            "NormalAnnotationExpr" to "NormEx",
            "NullLiteralExpr" to "Null",
            "ObjectCreationExpr" to "ObjEx",
            "Parameter" to "Prm",
            "PrimitiveType" to "Prim",
            "QualifiedNameExpr" to "Qua",
            "ReturnStmt" to "Ret",
            "SingleMemberAnnotationExpr" to "SMEx",
            "StringLiteralExpr" to "StrEx",
            "SuperExpr" to "SupEx",
            "SwitchEntryStmt" to "SwiEnt",
            "SwitchStmt" to "Switch",
            "SynchronizedStmt" to "Sync",
            "ThisExpr" to "This",
            "ThrowStmt" to "Thro",
            "TryStmt" to "Try",
            "TypeDeclarationStmt" to "TypeDec",
            "TypeExpr" to "Type",
            "TypeParameter" to "TypePar",
            "UnaryExpr:inverse" to "Inverse",
            "UnaryExpr:negative" to "Neg",
            "UnaryExpr:not" to "Not",
            "UnaryExpr:posDecrement" to "PosDec",
            "UnaryExpr:posIncrement" to "PosInc",
            "UnaryExpr:positive" to "Pos",
            "UnaryExpr:preDecrement" to "PreDec",
            "UnaryExpr:preIncrement" to "PreInc",
            "UnionType" to "Unio",
            "VariableDeclarationExpr" to "VDE",
            "VariableDeclarator" to "VD",
            "VariableDeclaratorId" to "VDID",
            "VoidType" to "Void",
            "WhileStmt" to "While",
            "WildcardType" to "Wild"
    )

    init {
        val nodeClass = node.javaClass
        type = nodeClass.simpleName
        rawType = nodeClass.simpleName
        if (node is ClassOrInterfaceType && node.isBoxedType) {
            type = primitiveType
        }
        operator = ""
        when (node) {
            is BinaryExpr -> operator = node.operator.toString()
            is UnaryExpr -> operator = node.operator.toString()
            is AssignExpr -> operator = node.operator.toString()
        }
        if (operator.isNotEmpty()) {
            type += ":$operator"
        }

        var nameToSplit = node.toString()
        if (isGenericParent) {
            nameToSplit = (node as ClassOrInterfaceType).name
            if (isLeaf) {
                type = genericClassType
            }
        }
        val splitNameParts = Common.splitToSubtokens(nameToSplit)
        splitName = splitNameParts.joinToString(Common.INTERNAL_SEPARATOR)

        var name = Common.normalizeName(node.toString(), Common.BLANK)
        when {
            name.length > Common.MAX_LABEL_LENGTH -> name = name.substring(0, Common.MAX_LABEL_LENGTH)
            node is ClassOrInterfaceType && node.isBoxedType -> name = node.toUnboxedType().toString()
        }

        if (Common.isMethod(node, type)) {
            splitName = Common.METHOD_NAME
            name = Common.METHOD_NAME
        }

        if (splitName.isEmpty()) {
            splitName = name
            if (node is IntegerLiteralExpr && !numericalValues.contains(splitName)) {
                splitName = num
            }
        }
    }

    fun getType(shorten: Boolean): String? {
        return if (shorten) {
            (shortTypes as java.util.Map<String, String>).getOrDefault(type, type)
        } else {
            type
        }
    }

    fun getName(): String {
        return splitName
    }

}
