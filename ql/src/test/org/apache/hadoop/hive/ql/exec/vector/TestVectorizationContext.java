/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hadoop.hive.ql.exec.vector;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.hadoop.hive.ql.exec.vector.expressions.FilterExprAndExpr;
import org.apache.hadoop.hive.ql.exec.vector.expressions.FilterExprOrExpr;
import org.apache.hadoop.hive.ql.exec.vector.expressions.VectorExpression;
import org.apache.hadoop.hive.ql.exec.vector.expressions.gen.DoubleColUnaryMinus;
import org.apache.hadoop.hive.ql.exec.vector.expressions.gen.FilterDoubleColLessDoubleScalar;
import org.apache.hadoop.hive.ql.exec.vector.expressions.gen.FilterLongColEqualLongScalar;
import org.apache.hadoop.hive.ql.exec.vector.expressions.gen.FilterLongColGreaterLongScalar;
import org.apache.hadoop.hive.ql.exec.vector.expressions.gen.FilterLongScalarGreaterLongColumn;
import org.apache.hadoop.hive.ql.exec.vector.expressions.gen.FilterStringColGreaterStringColumn;
import org.apache.hadoop.hive.ql.exec.vector.expressions.gen.FilterStringColGreaterStringScalar;
import org.apache.hadoop.hive.ql.exec.vector.expressions.gen.LongColAddLongColumn;
import org.apache.hadoop.hive.ql.exec.vector.expressions.gen.LongColModuloLongColumn;
import org.apache.hadoop.hive.ql.exec.vector.expressions.gen.LongColMultiplyLongColumn;
import org.apache.hadoop.hive.ql.exec.vector.expressions.gen.LongColSubtractLongColumn;
import org.apache.hadoop.hive.ql.exec.vector.expressions.gen.LongColUnaryMinus;
import org.apache.hadoop.hive.ql.exec.vector.expressions.gen.LongScalarSubtractLongColumn;
import org.apache.hadoop.hive.ql.metadata.HiveException;
import org.apache.hadoop.hive.ql.plan.ExprNodeColumnDesc;
import org.apache.hadoop.hive.ql.plan.ExprNodeConstantDesc;
import org.apache.hadoop.hive.ql.plan.ExprNodeDesc;
import org.apache.hadoop.hive.ql.plan.ExprNodeGenericFuncDesc;
import org.apache.hadoop.hive.ql.plan.api.OperatorType;
import org.apache.hadoop.hive.ql.udf.UDFOPMinus;
import org.apache.hadoop.hive.ql.udf.UDFOPMod;
import org.apache.hadoop.hive.ql.udf.UDFOPMultiply;
import org.apache.hadoop.hive.ql.udf.UDFOPNegative;
import org.apache.hadoop.hive.ql.udf.UDFOPPlus;
import org.apache.hadoop.hive.ql.udf.generic.GenericUDF;
import org.apache.hadoop.hive.ql.udf.generic.GenericUDFBridge;
import org.apache.hadoop.hive.ql.udf.generic.GenericUDFOPAnd;
import org.apache.hadoop.hive.ql.udf.generic.GenericUDFOPEqual;
import org.apache.hadoop.hive.ql.udf.generic.GenericUDFOPGreaterThan;
import org.apache.hadoop.hive.ql.udf.generic.GenericUDFOPLessThan;
import org.apache.hadoop.hive.ql.udf.generic.GenericUDFOPOr;
import org.apache.hadoop.hive.serde2.typeinfo.TypeInfoFactory;
import org.junit.Test;

public class TestVectorizationContext {

  @Test
  public void testArithmeticExpressionVectorization() throws HiveException {
    /**
     * Create original expression tree for following
     * (plus (minus (plus col1 col2) col3) (multiply col4 (mod col5 col6)) )
     */
    GenericUDFBridge udf1 = new GenericUDFBridge("+", true, UDFOPPlus.class.getCanonicalName());
    GenericUDFBridge udf2 = new GenericUDFBridge("-", true, UDFOPMinus.class.getCanonicalName());
    GenericUDFBridge udf3 = new GenericUDFBridge("*", true, UDFOPMultiply.class.getCanonicalName());
    GenericUDFBridge udf4 = new GenericUDFBridge("+", true, UDFOPPlus.class.getCanonicalName());
    GenericUDFBridge udf5 = new GenericUDFBridge("%", true, UDFOPMod.class.getCanonicalName());

    ExprNodeGenericFuncDesc sumExpr = new ExprNodeGenericFuncDesc();
    sumExpr.setGenericUDF(udf1);
    ExprNodeGenericFuncDesc minusExpr = new ExprNodeGenericFuncDesc();
    minusExpr.setGenericUDF(udf2);
    ExprNodeGenericFuncDesc multiplyExpr = new ExprNodeGenericFuncDesc();
    multiplyExpr.setGenericUDF(udf3);
    ExprNodeGenericFuncDesc sum2Expr = new ExprNodeGenericFuncDesc();
    sum2Expr.setGenericUDF(udf4);
    ExprNodeGenericFuncDesc modExpr = new ExprNodeGenericFuncDesc();
    modExpr.setGenericUDF(udf5);

    ExprNodeColumnDesc col1Expr = new  ExprNodeColumnDesc(Long.class, "col1", "table", false);
    ExprNodeColumnDesc col2Expr = new  ExprNodeColumnDesc(Long.class, "col2", "table", false);
    ExprNodeColumnDesc col3Expr = new  ExprNodeColumnDesc(Long.class, "col3", "table", false);
    ExprNodeColumnDesc col4Expr = new  ExprNodeColumnDesc(Long.class, "col4", "table", false);
    ExprNodeColumnDesc col5Expr = new  ExprNodeColumnDesc(Long.class, "col5", "table", false);
    ExprNodeColumnDesc col6Expr = new  ExprNodeColumnDesc(Long.class, "col6", "table", false);

    List<ExprNodeDesc> children1 = new ArrayList<ExprNodeDesc>(2);
    List<ExprNodeDesc> children2 = new ArrayList<ExprNodeDesc>(2);
    List<ExprNodeDesc> children3 = new ArrayList<ExprNodeDesc>(2);
    List<ExprNodeDesc> children4 = new ArrayList<ExprNodeDesc>(2);
    List<ExprNodeDesc> children5 = new ArrayList<ExprNodeDesc>(2);

    children1.add(minusExpr);
    children1.add(multiplyExpr);
    sumExpr.setChildren(children1);

    children2.add(sum2Expr);
    children2.add(col3Expr);
    minusExpr.setChildren(children2);

    children3.add(col1Expr);
    children3.add(col2Expr);
    sum2Expr.setChildren(children3);

    children4.add(col4Expr);
    children4.add(modExpr);
    multiplyExpr.setChildren(children4);

    children5.add(col5Expr);
    children5.add(col6Expr);
    modExpr.setChildren(children5);

    Map<String, Integer> columnMap = new HashMap<String, Integer>();
    columnMap.put("col1", 1);
    columnMap.put("col2", 2);
    columnMap.put("col3", 3);
    columnMap.put("col4", 4);
    columnMap.put("col5", 5);
    columnMap.put("col6", 6);

    //Generate vectorized expression
    VectorizationContext vc = new VectorizationContext(columnMap, 6);

    VectorExpression ve = vc.getVectorExpression(sumExpr);

    //Verify vectorized expression
    assertTrue(ve instanceof LongColAddLongColumn);
    assertEquals(2, ve.getChildExpressions().length);
    VectorExpression childExpr1 = ve.getChildExpressions()[0];
    VectorExpression childExpr2 = ve.getChildExpressions()[1];
    assertEquals(6, ve.getOutputColumn());

    assertTrue(childExpr1 instanceof LongColSubtractLongColumn);
    assertEquals(1, childExpr1.getChildExpressions().length);
    assertTrue(childExpr1.getChildExpressions()[0] instanceof LongColAddLongColumn);
    assertEquals(7, childExpr1.getOutputColumn());
    assertEquals(6, childExpr1.getChildExpressions()[0].getOutputColumn());

    assertTrue(childExpr2 instanceof LongColMultiplyLongColumn);
    assertEquals(1, childExpr2.getChildExpressions().length);
    assertTrue(childExpr2.getChildExpressions()[0] instanceof LongColModuloLongColumn);
    assertEquals(8, childExpr2.getOutputColumn());
    assertEquals(6, childExpr2.getChildExpressions()[0].getOutputColumn());
  }

  @Test
  public void testStringFilterExpressions() throws HiveException {
    ExprNodeColumnDesc col1Expr = new  ExprNodeColumnDesc(String.class, "col1", "table", false);
    ExprNodeConstantDesc constDesc = new ExprNodeConstantDesc("Alpha");

    GenericUDFOPGreaterThan udf = new GenericUDFOPGreaterThan();
    ExprNodeGenericFuncDesc exprDesc = new ExprNodeGenericFuncDesc();
    exprDesc.setGenericUDF(udf);
    List<ExprNodeDesc> children1 = new ArrayList<ExprNodeDesc>(2);
    children1.add(col1Expr);
    children1.add(constDesc);
    exprDesc.setChildren(children1);

    Map<String, Integer> columnMap = new HashMap<String, Integer>();
    columnMap.put("col1", 1);
    columnMap.put("col2", 2);

    VectorizationContext vc = new VectorizationContext(columnMap, 2);
    vc.setOperatorType(OperatorType.FILTER);

    VectorExpression ve = vc.getVectorExpression(exprDesc);

    assertTrue(ve instanceof FilterStringColGreaterStringScalar);
  }

  @Test
  public void testFilterStringColCompareStringColumnExpressions() throws HiveException {
    ExprNodeColumnDesc col1Expr = new  ExprNodeColumnDesc(String.class, "col1", "table", false);
    ExprNodeColumnDesc col2Expr = new  ExprNodeColumnDesc(String.class, "col2", "table", false);

    GenericUDFOPGreaterThan udf = new GenericUDFOPGreaterThan();
    ExprNodeGenericFuncDesc exprDesc = new ExprNodeGenericFuncDesc();
    exprDesc.setGenericUDF(udf);
    List<ExprNodeDesc> children1 = new ArrayList<ExprNodeDesc>(2);
    children1.add(col1Expr);
    children1.add(col2Expr);
    exprDesc.setChildren(children1);

    Map<String, Integer> columnMap = new HashMap<String, Integer>();
    columnMap.put("col1", 1);
    columnMap.put("col2", 2);

    VectorizationContext vc = new VectorizationContext(columnMap, 2);
    vc.setOperatorType(OperatorType.FILTER);

    VectorExpression ve = vc.getVectorExpression(exprDesc);

    assertTrue(ve instanceof FilterStringColGreaterStringColumn);
  }

  @Test
  public void testFloatInExpressions() throws HiveException {
    ExprNodeColumnDesc col1Expr = new ExprNodeColumnDesc(Float.class, "col1", "table", false);
    ExprNodeConstantDesc constDesc = new ExprNodeConstantDesc(new Integer(10));

    GenericUDFBridge udf = new GenericUDFBridge("+", false, UDFOPPlus.class.getCanonicalName());
    ExprNodeGenericFuncDesc exprDesc = new ExprNodeGenericFuncDesc();
    exprDesc.setGenericUDF(udf);

    List<ExprNodeDesc> children1 = new ArrayList<ExprNodeDesc>(2);
    children1.add(col1Expr);
    children1.add(constDesc);
    exprDesc.setChildren(children1);

    Map<String, Integer> columnMap = new HashMap<String, Integer>();
    columnMap.put("col1", 0);

    VectorizationContext vc = new VectorizationContext(columnMap, 2);
    vc.setOperatorType(OperatorType.SELECT);

    VectorExpression ve = vc.getVectorExpression(exprDesc);

    assertTrue(ve.getOutputType().equalsIgnoreCase("double"));
  }

  @Test
  public void testVectorizeAndOrExpression() throws HiveException {
    ExprNodeColumnDesc col1Expr = new ExprNodeColumnDesc(Integer.class, "col1", "table", false);
    ExprNodeConstantDesc constDesc = new ExprNodeConstantDesc(new Integer(10));

    GenericUDFOPGreaterThan udf = new GenericUDFOPGreaterThan();
    ExprNodeGenericFuncDesc greaterExprDesc = new ExprNodeGenericFuncDesc();
    greaterExprDesc.setGenericUDF(udf);
    List<ExprNodeDesc> children1 = new ArrayList<ExprNodeDesc>(2);
    children1.add(col1Expr);
    children1.add(constDesc);
    greaterExprDesc.setChildren(children1);

    ExprNodeColumnDesc col2Expr = new ExprNodeColumnDesc(Float.class, "col2", "table", false);
    ExprNodeConstantDesc const2Desc = new ExprNodeConstantDesc(new Float(1.0));

    GenericUDFOPLessThan udf2 = new GenericUDFOPLessThan();
    ExprNodeGenericFuncDesc lessExprDesc = new ExprNodeGenericFuncDesc();
    lessExprDesc.setGenericUDF(udf2);
    List<ExprNodeDesc> children2 = new ArrayList<ExprNodeDesc>(2);
    children2.add(col2Expr);
    children2.add(const2Desc);
    lessExprDesc.setChildren(children2);

    GenericUDFOPAnd andUdf = new GenericUDFOPAnd();
    ExprNodeGenericFuncDesc andExprDesc = new ExprNodeGenericFuncDesc();
    andExprDesc.setGenericUDF(andUdf);
    List<ExprNodeDesc> children3 = new ArrayList<ExprNodeDesc>(2);
    children3.add(greaterExprDesc);
    children3.add(lessExprDesc);
    andExprDesc.setChildren(children3);

    Map<String, Integer> columnMap = new HashMap<String, Integer>();
    columnMap.put("col1", 0);
    columnMap.put("col2", 1);

    VectorizationContext vc = new VectorizationContext(columnMap, 2);
    vc.setOperatorType(OperatorType.FILTER);

    VectorExpression ve = vc.getVectorExpression(andExprDesc);

    assertEquals(ve.getClass(), FilterExprAndExpr.class);
    assertEquals(ve.getChildExpressions()[0].getClass(), FilterLongColGreaterLongScalar.class);
    assertEquals(ve.getChildExpressions()[1].getClass(), FilterDoubleColLessDoubleScalar.class);

    GenericUDFOPOr orUdf = new GenericUDFOPOr();
    ExprNodeGenericFuncDesc orExprDesc = new ExprNodeGenericFuncDesc();
    orExprDesc.setGenericUDF(orUdf);
    List<ExprNodeDesc> children4 = new ArrayList<ExprNodeDesc>(2);
    children4.add(greaterExprDesc);
    children4.add(lessExprDesc);
    orExprDesc.setChildren(children4);


    VectorExpression veOr = vc.getVectorExpression(orExprDesc);

    assertEquals(veOr.getClass(), FilterExprOrExpr.class);
    assertEquals(veOr.getChildExpressions()[0].getClass(), FilterLongColGreaterLongScalar.class);
    assertEquals(veOr.getChildExpressions()[1].getClass(), FilterDoubleColLessDoubleScalar.class);
  }

  @Test
  public void testVectorizeScalarColumnExpression() throws HiveException {
    ExprNodeGenericFuncDesc scalarMinusConstant = new ExprNodeGenericFuncDesc();
    GenericUDF gudf = new GenericUDFBridge("-", true, UDFOPMinus.class.getCanonicalName());
    scalarMinusConstant.setGenericUDF(gudf);
    List<ExprNodeDesc> children = new ArrayList<ExprNodeDesc>(2);
    ExprNodeConstantDesc constDesc = new ExprNodeConstantDesc(TypeInfoFactory.longTypeInfo, 20);
    ExprNodeColumnDesc colDesc = new ExprNodeColumnDesc(Long.class, "a", "table", false);

    children.add(constDesc);
    children.add(colDesc);

    scalarMinusConstant.setChildren(children);

    Map<String, Integer> columnMap = new HashMap<String, Integer>();
    columnMap.put("a", 0);

    VectorizationContext vc = new VectorizationContext(columnMap, 2);
    VectorExpression ve = vc.getVectorExpression(scalarMinusConstant);

    assertEquals(ve.getClass(), LongScalarSubtractLongColumn.class);
  }

  @Test
  public void testFilterWithNegativeScalar() throws HiveException {
    ExprNodeColumnDesc col1Expr = new  ExprNodeColumnDesc(Integer.class, "col1", "table", false);
    ExprNodeConstantDesc constDesc = new ExprNodeConstantDesc(new Integer(-10));

    GenericUDFOPGreaterThan udf = new GenericUDFOPGreaterThan();
    ExprNodeGenericFuncDesc exprDesc = new ExprNodeGenericFuncDesc();
    exprDesc.setGenericUDF(udf);
    List<ExprNodeDesc> children1 = new ArrayList<ExprNodeDesc>(2);
    children1.add(col1Expr);
    children1.add(constDesc);
    exprDesc.setChildren(children1);

    Map<String, Integer> columnMap = new HashMap<String, Integer>();
    columnMap.put("col1", 1);
    columnMap.put("col2", 2);

    VectorizationContext vc = new VectorizationContext(columnMap, 2);
    vc.setOperatorType(OperatorType.FILTER);

    VectorExpression ve = vc.getVectorExpression(exprDesc);

    assertTrue(ve instanceof FilterLongColGreaterLongScalar);
  }

  @Test
  public void testUnaryMinusColumnLong() throws HiveException {
    ExprNodeColumnDesc col1Expr = new  ExprNodeColumnDesc(Integer.class, "col1", "table", false);
    ExprNodeGenericFuncDesc negExprDesc = new ExprNodeGenericFuncDesc();
    GenericUDF gudf = new GenericUDFBridge("-", true, UDFOPNegative.class.getCanonicalName());
    negExprDesc.setGenericUDF(gudf);
    List<ExprNodeDesc> children = new ArrayList<ExprNodeDesc>(1);
    children.add(col1Expr);
    negExprDesc.setChildren(children);
    Map<String, Integer> columnMap = new HashMap<String, Integer>();
    columnMap.put("col1", 1);
    VectorizationContext vc = new VectorizationContext(columnMap, 1);
    vc.setOperatorType(OperatorType.SELECT);

    VectorExpression ve = vc.getVectorExpression(negExprDesc);

    assertTrue( ve instanceof LongColUnaryMinus);
  }

  @Test
  public void testUnaryMinusColumnDouble() throws HiveException {
    ExprNodeColumnDesc col1Expr = new  ExprNodeColumnDesc(Float.class, "col1", "table", false);
    ExprNodeGenericFuncDesc negExprDesc = new ExprNodeGenericFuncDesc();
    GenericUDF gudf = new GenericUDFBridge("-", true, UDFOPNegative.class.getCanonicalName());
    negExprDesc.setGenericUDF(gudf);
    List<ExprNodeDesc> children = new ArrayList<ExprNodeDesc>(1);
    children.add(col1Expr);
    negExprDesc.setChildren(children);
    Map<String, Integer> columnMap = new HashMap<String, Integer>();
    columnMap.put("col1", 1);
    VectorizationContext vc = new VectorizationContext(columnMap, 1);
    vc.setOperatorType(OperatorType.SELECT);

    VectorExpression ve = vc.getVectorExpression(negExprDesc);

    assertTrue( ve instanceof DoubleColUnaryMinus);
  }

  @Test
  public void testFilterScalarCompareColumn() throws HiveException {
    ExprNodeGenericFuncDesc scalarGreaterColExpr = new ExprNodeGenericFuncDesc();
    GenericUDFOPGreaterThan gudf = new GenericUDFOPGreaterThan();
    scalarGreaterColExpr.setGenericUDF(gudf);
    List<ExprNodeDesc> children = new ArrayList<ExprNodeDesc>(2);
    ExprNodeConstantDesc constDesc =
        new ExprNodeConstantDesc(TypeInfoFactory.longTypeInfo, 20);
    ExprNodeColumnDesc colDesc =
        new ExprNodeColumnDesc(Long.class, "a", "table", false);

    children.add(constDesc);
    children.add(colDesc);

    scalarGreaterColExpr.setChildren(children);

    Map<String, Integer> columnMap = new HashMap<String, Integer>();
    columnMap.put("a", 0);

    VectorizationContext vc = new VectorizationContext(columnMap, 2);
    vc.setOperatorType(OperatorType.FILTER);
    VectorExpression ve = vc.getVectorExpression(scalarGreaterColExpr);
    assertEquals(FilterLongScalarGreaterLongColumn.class, ve.getClass());
  }

  @Test
  public void testFilterBooleanColumnCompareBooleanScalar() throws HiveException {
    ExprNodeGenericFuncDesc colEqualScalar = new ExprNodeGenericFuncDesc();
    GenericUDFOPEqual gudf = new GenericUDFOPEqual();
    colEqualScalar.setGenericUDF(gudf);
    List<ExprNodeDesc> children = new ArrayList<ExprNodeDesc>(2);
    ExprNodeConstantDesc constDesc =
        new ExprNodeConstantDesc(TypeInfoFactory.booleanTypeInfo, 20);
    ExprNodeColumnDesc colDesc =
        new ExprNodeColumnDesc(Boolean.class, "a", "table", false);

    children.add(colDesc);
    children.add(constDesc);

    colEqualScalar.setChildren(children);

    Map<String, Integer> columnMap = new HashMap<String, Integer>();
    columnMap.put("a", 0);

    VectorizationContext vc = new VectorizationContext(columnMap, 2);
    vc.setOperatorType(OperatorType.FILTER);
    VectorExpression ve = vc.getVectorExpression(colEqualScalar);
    assertEquals(FilterLongColEqualLongScalar.class, ve.getClass());
  }
}
