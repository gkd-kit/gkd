package li.songe.room_processor

import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.validate
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.ksp.KotlinPoetKspPreview
import com.squareup.kotlinpoet.ksp.toAnnotationSpec
import com.squareup.kotlinpoet.ksp.toClassName
import kotlin.concurrent.thread


class RoomProcessor(private val environment: SymbolProcessorEnvironment) : SymbolProcessor {
//    private val logger = environment.logger

    private val classAnnotationName = "androidx.room.Entity"
    private val propertyAnnotationName = "androidx.room.ColumnInfo"


    /**
     * return value see https://book.kotlincn.net/text/ksp-multi-round.html
     */
    @OptIn(KotlinPoetKspPreview::class)
    override fun process(resolver: Resolver): List<KSAnnotated> {
        val class2property2nameMap =
            mutableMapOf<String, Pair<String, MutableMap<String, String>>>()

        val symbols = resolver
            .getSymbolsWithAnnotation(classAnnotationName)
            .filterIsInstance<KSClassDeclaration>()
        if (!symbols.iterator().hasNext()) {
            return emptyList()
        }
        symbols
            .forEach {
                val argument = (it as KSAnnotated)
                    .annotations
                    .first { ksAnnotation ->
                        val ksName = ksAnnotation.toAnnotationSpec().typeName.toString()
                        ksName == classAnnotationName
                    }
                    .arguments.firstOrNull { argument -> argument.name?.asString() == "tableName" }
                    ?: return@forEach
                val tableName = argument.value?.toString() ?: return@forEach
                val classCanonicalName = it.toClassName().canonicalName

                val property2valueMap = mutableMapOf<String, String>()
                it.declarations.filterIsInstance<KSPropertyDeclaration>()
                    .forEach ksPropertyForEach@{ ksPropertyDeclaration ->
                        val propertyAnnotation =
                            ksPropertyDeclaration.annotations.firstOrNull { ksAnnotation ->
                                ksAnnotation.toAnnotationSpec().typeName.toString() == propertyAnnotationName
                            } ?: return@ksPropertyForEach
                        val propertyName = ksPropertyDeclaration.simpleName.asString()
                        val valueName =
                            propertyAnnotation.arguments.firstOrNull { argument -> argument.name?.asString() == "name" }?.value?.toString()
                                ?: return@ksPropertyForEach
                        property2valueMap[propertyName] = valueName
                    }
                class2property2nameMap[classCanonicalName] = Pair(tableName, property2valueMap)
            }

        val typeSpec = TypeSpec
            .objectBuilder("RoomAnnotation")
            .addFunction(
                FunSpec.builder("getTableName")
                    .addParameter("className", String::class)
                    .returns(String::class)
                    .addCode("return when (className) { \n")
                    .apply {
                        class2property2nameMap.forEach { (t, u) ->
                            addCode(indent(1) + "%S -> %S \n", t, u.first)
                        }
                    }
                    .addCode(
                        indent(1) + "%S -> %S \n",
                        "r-" + System.currentTimeMillis(),
                        "avoid_compile_error"
                    )
                    .addCode(
                        indent(1) + "else -> throw Exception(%P) \n",
                        "not found className : \$className"
                    )
                    .addCode("}")
                    .build()
            )
            .addFunction(
                FunSpec.builder("getColumnName")
                    .addParameter("className", String::class)
                    .addParameter("propertyName", String::class)
                    .returns(String::class)
                    .addCode("return when (className) { \n")
                    .apply {
                        class2property2nameMap.forEach { (className, u) ->
                            addCode(indent(1) + "%S -> ", className)
                            addCode("when (propertyName) { \n")
                            u.second.forEach { (propertyName, columnName) ->
                                addCode(indent(2) + "%S -> %S \n", propertyName, columnName)
                            }
                            addCode(
                                indent(2) + "%S -> %S \n",
                                "r-" + System.currentTimeMillis(),
                                "avoid_compile_error"
                            )
                            addCode(
                                indent(2) + "else -> throw Exception(%P) \n",
                                "not found columnName : \$className#\$propertyName"
                            )
                            addCode(indent(1) + "}\n")
                        }
                    }
                    .addCode(
                        indent(1) + "%S -> %S \n",
                        "r-" + System.currentTimeMillis(),
                        "avoid_compile_error"
                    )
                    .addCode(
                        indent(1) + "else -> throw Exception(%P) \n",
                        "not found className : \$className"
                    )
                    .addCode("}")
                    .build()
            )
            .build()

        val kotlinFile = FileSpec
            .builder("li.songe.room_annotation", "RoomAnnotation")
            .addImport(Exception::class, "")
            .addType(typeSpec).build()

//        thanks https://github.com/Morfly/ksp-sample
        val dependencies = Dependencies(false, *resolver.getAllFiles().toList().toTypedArray())
        thread {
            environment
                .codeGenerator
                .createNewFile(dependencies, "li.songe.room_annotation", "RoomAnnotation")
                .bufferedWriter().use { writer ->
                    kotlinFile.writeTo(writer)
                }
        }
        return symbols.filterNot { it.validate() }.toList()
    }

    companion object {
        fun indent(n: Int) = List(n * 2) { "\u0020" }.joinToString("")
    }

}

