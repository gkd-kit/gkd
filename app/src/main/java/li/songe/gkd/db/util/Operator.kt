package li.songe.gkd.db.util

import li.songe.room_annotation.RoomAnnotation
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.KProperty1

object Operator {
    infix fun <L1 : Any, R1, L2 : Any, R2, T : Any> Expression<L1, R1, T>.and(other: Expression<L2, R2, T>) =
        Expression(this, "AND", other, tableClass)

    infix fun <L1 : Any, R1, L2 : Any, R2, T : Any> Expression<L1, R1, T>.or(other: Expression<L2, R2, T>) =
        Expression(this, "OR", other, tableClass)


//    TODO 当同时设置 Property1 时, 代码失效
//    还需要写 Int, Long, String, Boolean 等多种类型的重载, 这种重复性很高,工作量指数级增长的工作确实需要联合类型
    inline fun <reified T : Any, V, V2> KMutableProperty1<T, V>.baseOperator(
        value: V2,
        operator: String
    ) =
        Expression(
            RoomAnnotation.getColumnName(T::class.java.name, name),
            operator,
            value,
            T::class
        )

    inline infix fun <reified T : Any, reified V> KMutableProperty1<T, V>.eq(value: V) =
        baseOperator(value, "==")

    inline infix fun <reified T : Any, reified V> KMutableProperty1<T, V>.neq(value: V) =
        baseOperator(value, "!=")

    inline infix fun <reified T : Any, reified V> KMutableProperty1<T, V>.less(value: V) =
        baseOperator(value, "<")

    inline infix fun <reified T : Any, reified V> KMutableProperty1<T, V>.lessEq(value: V) =
        baseOperator(value, "<=")

    inline infix fun <reified T : Any, reified V> KMutableProperty1<T, V>.greater(value: V) =
        baseOperator(value, ">")

    inline infix fun <reified T : Any, reified V> KMutableProperty1<T, V>.greaterEq(value: V) =
        baseOperator(value, ">=")

    inline infix fun <reified T : Any, reified V> KMutableProperty1<T, V>.inList(value: List<V>) =
        baseOperator(value, "IN")

    inline infix fun <reified T : Any, reified V> KMutableProperty1<T, V>.glob(value: GlobString) =
        baseOperator(value, "GLOB")

    inline infix fun <reified T : Any, reified V> KMutableProperty1<T, V>.like(value: LikeString) =
        baseOperator(value, "LIKE")

    inline fun <reified T : Any, V, V2> KProperty1<T, V>.baseOperator(
        value: V2,
        operator: String
    ) =
        Expression(
            RoomAnnotation.getColumnName(T::class.java.name, name),
            operator,
            value,
            T::class
        )

//    inline infix fun <reified T : Any, reified V> KProperty1<T, V>.eq(value: V): Expression<String, V, T> {
//        return baseOperator(value, "==")
//    }
//    inline infix fun <reified T : Any, reified V> KProperty1<T, V>.neq(value: V) =
//        baseOperator(value, "!=")
//
//    inline infix fun <reified T : Any, reified V> KProperty1<T, V>.less(value: V) =
//        baseOperator(value, "<")
//
//    inline infix fun <reified T : Any, reified V> KProperty1<T, V>.lessEq(value: V) =
//        baseOperator(value, "<=")
//
//    inline infix fun <reified T : Any, reified V> KProperty1<T, V>.greater(value: V) =
//        baseOperator(value, ">")
//
//    inline infix fun <reified T : Any, reified V> KProperty1<T, V>.greaterEq(value: V) =
//        baseOperator(value, ">=")
//
//    inline infix fun <reified T : Any, reified V> KProperty1<T, V>.inList(value: List<V>) =
//        baseOperator(value, "IN")
//
//    inline infix fun <reified T : Any, reified V> KProperty1<T, V>.glob(value: GlobString) =
//        baseOperator(value, "GLOB")
//
//    inline infix fun <reified T : Any, reified V> KProperty1<T, V>.like(value: LikeString) =
//        baseOperator(value, "LIKE")

}