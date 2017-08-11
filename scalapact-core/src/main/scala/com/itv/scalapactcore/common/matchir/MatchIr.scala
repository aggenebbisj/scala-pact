package com.itv.scalapactcore.common.matchir

import scala.xml.{Elem, XML, Node}

import com.itv.scalapactcore.common.ColourOuput._

import argonaut._

object MatchIr extends XmlConversionFunctions with JsonConversionFunctions with PrimitiveConversionFunctions {

  def fromXml(xmlString: String): Option[IrNode] =
    safeStringToXml(xmlString).map { elem =>
      nodeToIrNode(scala.xml.Utility.trim(elem))
    }

  def fromJSON(jsonString: String): Option[IrNode] =
    Parse.parseOption(jsonString).flatMap { json =>
      jsonRootToIrNode(json)
    }

}

trait JsonConversionFunctions {

  val rootNodeLabel = "(--root node--)"
  val unnamedNodeLabel = "(--node has no label--)"

  protected def jsonToIrNode(label: String, json: Json): IrNode = {
    json match {
      case j: Json if j.isArray =>
        IrNode(label, jsonArrayToIrNodeList(label, j))

      case j: Json if j.isObject =>
        IrNode(label, jsonObjectToIrNodeList(j))

      case j: Json if j.isNull =>
        IrNode(label, IrNullNode)

      case j: Json if j.isNumber =>
        IrNode(label, j.number.flatMap(_.toDouble).map(d => IrNumberNode(d)))

      case j: Json if j.isBool =>
        IrNode(label, j.bool.map(IrBooleanNode))

      case j: Json if j.isString =>
        IrNode(label, j.string.map(IrStringNode))
    }

  }

  protected def jsonObjectToIrNodeList(json: Json): List[IrNode] =
    json.objectFieldsOrEmpty.map(l => if(l.isEmpty) unnamedNodeLabel else l).map(p => json.field(p).map(q => jsonToIrNode(p, q))).collect { case Some(s) => s }

  protected def jsonArrayToIrNodeList(parentLabel: String, json: Json): List[IrNode] = {
    json.arrayOrEmpty.map(j => jsonToIrNode(parentLabel, j))
  }

  protected def jsonRootToIrNode(json: Json): Option[IrNode] =
    json match {
      case j: Json if j.isArray =>
        Option(
          IrNode(rootNodeLabel, jsonArrayToIrNodeList(unnamedNodeLabel, j))
        )

      case j: Json if j.isObject =>
        Option(
          IrNode(rootNodeLabel, jsonObjectToIrNodeList(j))
        )

      case _ =>
        println("JSON was not an object or an array".red)
        None
    }

}

trait XmlConversionFunctions extends PrimitiveConversionFunctions {

  protected def convertAttributes(attributes: Map[String, String]): Map[String, IrNodePrimitive] =
    attributes.flatMap {
      case (k, v) if v == null => Map(k -> IrNullNode)
      case (k, v) if v.matches(isNumericValueRegex) => safeStringToDouble(v).map(IrNumberNode).map(vv => Map(k -> vv)).getOrElse(Map.empty[String, IrNumberNode])
      case (k, v) if v.matches(isBooleanValueRegex) => safeStringToBoolean(v).map(IrBooleanNode).map(vv => Map(k -> vv)).getOrElse(Map.empty[String, IrBooleanNode])
      case (k, v) => Map(k -> IrStringNode(v))
    }

  protected def childNodesToValueMaybePrimitive(nodes: List[Node], value: String): Option[IrNodePrimitive] =
    nodes match {
      case Nil if value == null => Option(IrNullNode)
      case Nil if value.isEmpty => None
      case Nil if value.matches(isNumericValueRegex) => safeStringToDouble(value).map(IrNumberNode)
      case Nil if value.matches(isBooleanValueRegex) => safeStringToBoolean(value).map(IrBooleanNode)
      case Nil => Option(IrStringNode(value))
      case _ => None
    }

  protected def extractNodeValue(node: Node): Option[IrNodePrimitive] =
    childNodesToValueMaybePrimitive(node.child.flatMap(_.child).toList, node.child.text)

  protected def extractNodeChildren(node: Node): List[IrNode] =
    node.child.toList.map(nodeToIrNode)

  protected def nodeToIrNode(node: Node): IrNode =
    extractNodeValue(node) match {
      case nodeValue: Some[IrNodePrimitive] =>
        IrNode(node.label, nodeValue, Nil, Option(node.prefix), convertAttributes(node.attributes.asAttrMap), IrNodePathEmpty())

      case None =>
        IrNode(node.label, None, extractNodeChildren(node), Option(node.prefix), convertAttributes(node.attributes.asAttrMap), IrNodePathEmpty())
    }

}

trait PrimitiveConversionFunctions {

  // Maybe negative, must have digits, may have decimal and if so must have a
  // digit after it, can have more trailing digits.
  val isNumericValueRegex = """^-?\d+\.?\d*$"""
  val isBooleanValueRegex = """^(true|false)$"""

  def safeStringToDouble(str: String): Option[Double] =
    try {
      Option(str.toDouble)
    } catch {
      case _: Throwable =>
        println(s"Failed to convert string '$str' to number (double)".red)
        None
    }

  def safeStringToBoolean(str: String): Option[Boolean] =
    try {
      Option(str.toBoolean)
    } catch {
      case _: Throwable =>
        println(s"Failed to convert string '$str' to boolean".red)
        None
    }

  def safeStringToXml(str: String): Option[Elem] =
    try {
      Option(XML.loadString(str))
    } catch {
      case _: Throwable =>
        println(s"Failed to convert string '$str' to xml".red)
        None
    }

}