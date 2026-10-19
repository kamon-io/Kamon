package kamon.instrumentation.jdbc.utils

import net.sf.jsqlparser.schema.Table
import net.sf.jsqlparser.statement.{
  Block,
  Commit,
  CreateFunctionalStatement,
  DeclareStatement,
  DescribeStatement,
  ExplainStatement,
  ResetStatement,
  RollbackStatement,
  SavepointStatement,
  SetStatement,
  ShowColumnsStatement,
  ShowStatement,
  StatementVisitor,
  Statements,
  UseStatement
}
import net.sf.jsqlparser.statement.alter.{Alter, AlterSession}
import net.sf.jsqlparser.statement.alter.sequence.AlterSequence
import net.sf.jsqlparser.statement.comment.Comment
import net.sf.jsqlparser.statement.create.index.CreateIndex
import net.sf.jsqlparser.statement.create.schema.CreateSchema
import net.sf.jsqlparser.statement.create.sequence.CreateSequence
import net.sf.jsqlparser.statement.create.synonym.CreateSynonym
import net.sf.jsqlparser.statement.create.table.CreateTable
import net.sf.jsqlparser.statement.create.view.{AlterView, CreateView}
import net.sf.jsqlparser.statement.delete.Delete
import net.sf.jsqlparser.statement.drop.Drop
import net.sf.jsqlparser.statement.execute.Execute
import net.sf.jsqlparser.statement.grant.Grant
import net.sf.jsqlparser.statement.insert.Insert
import net.sf.jsqlparser.statement.merge.Merge
import net.sf.jsqlparser.statement.replace.Replace
import net.sf.jsqlparser.statement.select.{
  FromItemVisitor,
  LateralSubSelect,
  ParenthesisFromItem,
  PlainSelect,
  Select,
  SelectVisitor,
  SetOperationList,
  SubJoin,
  SubSelect,
  TableFunction,
  ValuesList,
  WithItem
}
import net.sf.jsqlparser.statement.show.ShowTablesStatement
import net.sf.jsqlparser.statement.truncate.Truncate
import net.sf.jsqlparser.statement.update.Update
import net.sf.jsqlparser.statement.upsert.Upsert
import net.sf.jsqlparser.statement.values.ValuesStatement

class SqlVisitor(var operation: String) extends StatementVisitor with FromItemVisitor with SelectVisitor {
  override def visit(comment: Comment): Unit = {}

  override def visit(commit: Commit): Unit = operation = "commit"

  override def visit(delete: Delete): Unit = operation = s"delete ${delete.getTable.toString}"

  override def visit(update: Update): Unit = operation = s"update ${update.getTable.toString}"

  override def visit(insert: Insert): Unit = operation = s"insert ${insert.getTable.toString}"

  override def visit(replace: Replace): Unit = operation = s"replace ${replace.getTable.toString}"

  override def visit(drop: Drop): Unit = operation = s"drop ${drop.getName.toString}"

  override def visit(truncate: Truncate): Unit = operation = s"truncate ${truncate.getTable.toString}"

  override def visit(createIndex: CreateIndex): Unit = operation = s"create_index ${createIndex.getIndex.getName}"

  override def visit(aThis: CreateSchema): Unit = operation = s"create_schema ${aThis.getSchemaName}"

  override def visit(createTable: CreateTable): Unit = operation = s"create_table ${createTable.getTable.toString}"

  override def visit(createView: CreateView): Unit = operation = s"create_view ${createView.getView.toString}"

  override def visit(alterView: AlterView): Unit = operation = s"alter_view ${alterView.getView.toString}"

  override def visit(alter: Alter): Unit = operation = s"alter ${alter.getTable.toString}"

  override def visit(stmts: Statements): Unit = {}

  override def visit(execute: Execute): Unit = {}

  override def visit(set: SetStatement): Unit = {}

  override def visit(set: ShowColumnsStatement): Unit = {}

  override def visit(showTables: ShowTablesStatement): Unit = {}

  override def visit(merge: Merge): Unit = {}

  override def visit(select: Select): Unit = select.getSelectBody.accept(this)

  override def visit(plainSelect: PlainSelect): Unit = plainSelect.getFromItem.accept(this)

  override def visit(tableName: Table): Unit = operation = s"select ${tableName.toString}"

  override def visit(subSelect: SubSelect): Unit = operation = s"select ${subSelect.getAlias.toString}"

  override def visit(subjoin: SubJoin): Unit = operation = s"select ${subjoin.getAlias.toString}"

  override def visit(lateralSubSelect: LateralSubSelect): Unit =
    operation = s"select ${lateralSubSelect.getAlias.toString}"

  override def visit(valuesList: ValuesList): Unit = operation = s"select ${valuesList.getAlias}"

  override def visit(tableFunction: TableFunction): Unit = operation = s"select ${tableFunction.getAlias}"

  override def visit(aThis: ParenthesisFromItem): Unit = operation = s"select ${aThis.getAlias.toString}"

  override def visit(setOpList: SetOperationList): Unit = {}

  override def visit(withItem: WithItem): Unit = {}

  override def visit(upsert: Upsert): Unit = operation = s"upsert ${upsert.getTable.toString}"

  override def visit(use: UseStatement): Unit = {}

  override def visit(block: Block): Unit = {}

  override def visit(values: ValuesStatement): Unit = operation = "values"

  override def visit(describe: DescribeStatement): Unit = {}

  override def visit(aThis: ExplainStatement): Unit = operation = "explain"

  override def visit(aThis: ShowStatement): Unit = operation = "show"

  override def visit(aThis: DeclareStatement): Unit = {}

  override def visit(grant: Grant): Unit = operation = "grant"

  override def visit(createSequence: CreateSequence): Unit =
    operation = s"create_sequnce ${createSequence.sequence.getName}"

  override def visit(alterSequence: AlterSequence): Unit =
    operation = s"alter_sequence ${alterSequence.sequence.getName}"

  override def visit(createFunctionalStatement: CreateFunctionalStatement): Unit = {}

  override def visit(createSynonym: CreateSynonym): Unit = {}

  override def visit(savepointStatement: SavepointStatement): Unit = {}

  override def visit(rollbackStatement: RollbackStatement): Unit = {}

  override def visit(reset: ResetStatement): Unit = {}

  override def visit(alterSession: AlterSession): Unit = {}
}
