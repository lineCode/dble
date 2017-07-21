package io.mycat.plan.common.item.function.sumfunc;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;

import com.alibaba.druid.sql.ast.SQLExpr;
import com.alibaba.druid.sql.ast.SQLOrderBy;
import com.alibaba.druid.sql.ast.expr.SQLAggregateExpr;
import com.alibaba.druid.sql.ast.expr.SQLAggregateOption;
import com.alibaba.druid.sql.ast.expr.SQLCharExpr;
import com.alibaba.druid.sql.ast.statement.SQLSelectOrderByItem;

import io.mycat.net.mysql.RowDataPacket;
import io.mycat.plan.Order;
import io.mycat.plan.common.field.Field;
import io.mycat.plan.common.item.FieldTypes;
import io.mycat.plan.common.item.Item;
import io.mycat.plan.common.item.function.ItemFuncKeyWord;
import io.mycat.plan.common.time.MySQLTime;


public class ItemFuncGroupConcat extends ItemSum {
	protected StringBuilder resultSb;
	protected String seperator;
	protected boolean no_appended;
	private List<Order> orders;
	protected boolean always_null;// 如果参数存在null时

	public ItemFuncGroupConcat(List<Item> selItems, boolean distinct, List<Order> orders, String is_separator,
			boolean isPushDown, List<Field> fields) {
		super(selItems, isPushDown, fields);
		this.orders = orders;
		seperator = is_separator;
		this.resultSb = new StringBuilder();
		this.always_null = false;
		setDistinct(distinct);
	}

	@Override
	public Sumfunctype sumType() {
		return Sumfunctype.GROUP_CONCAT_FUNC;
	}

	@Override
	public String funcName() {
		return "GROUP_CONCAT";
	}

	@Override
	public ItemResult resultType() {
		return ItemResult.STRING_RESULT;
	}

	@Override
	public FieldTypes fieldType() {
		return FieldTypes.MYSQL_TYPE_VARCHAR;
	}

	@Override
	public void cleanup() {
		super.cleanup();
	}

	@Override
	public void clear() {
		resultSb.setLength(0);
		nullValue = true;
		no_appended = true;
	}

	@Override
	public Object getTransAggObj() {
		throw new RuntimeException("Group_concat should not use direct groupby!");
	}

	@Override
	public int getTransSize() {
		throw new RuntimeException("Group_concat should not use direct groupby!");
	}

	@Override
	public boolean add(RowDataPacket row, Object tranObject) {
		if (always_null)
			return false;
		String rowStr = new String();
		for (int i = 0; i < getArgCount(); i++) {
			Item item = args.get(i);
			String s = item.valStr();
			if (item.isNull())
				return false;
			rowStr += s;
		}
		nullValue = false;
		if (resultSb.length() > 0)
			resultSb.append(seperator);
		resultSb.append(rowStr);
		return false;
	}

	@Override
	public boolean setup() {
		always_null = false;
		for (int i = 0; i < getArgCount(); i++) {
			Item item = args.get(i);
			if (item.canValued()) {
				if (item.isNull()) {
					always_null = true;
					return false;
				}
			}
		}
		return false;
	}

	@Override
	public boolean fixFields() {
		super.fixFields();
		nullValue = true;
		fixed = true;
		return false;
	}

	@Override
	public String valStr() {
		if (aggr != null)
			aggr.endup();
		if (nullValue)
			return null;
		return resultSb.toString();
	}

	@Override
	public BigDecimal valReal() {
		String res = valStr();
		if (res == null)
			return BigDecimal.ZERO;
		else
			try {
				return new BigDecimal(res);
			} catch (Exception e) {
				logger.info("group_concat val_real() convert exception, string value is: " + res);
				return BigDecimal.ZERO;
			}
	}

	@Override
	public BigInteger valInt() {
		String res = valStr();
		if (res == null)
			return BigInteger.ZERO;
		else
			try {
				return new BigInteger(res);
			} catch (Exception e) {
				logger.info("group_concat val_int() convert exception, string value is: " + res);
				return BigInteger.ZERO;
			}
	}

	@Override
	public BigDecimal valDecimal() {
		return valDecimalFromString();
	}

	@Override
	public boolean getDate(MySQLTime ltime, long fuzzydate) {
		return getDateFromString(ltime, fuzzydate);
	}

	@Override
	public boolean getTime(MySQLTime ltime) {
		return getTimeFromString(ltime);
	}

	@Override
	public boolean pushDownAdd(RowDataPacket row) {
		throw new RuntimeException("not implement");
	}

	@Override
	public SQLExpr toExpression() {
		SQLAggregateExpr aggregate = new SQLAggregateExpr(funcName());
		if (has_with_distinct()) {
			aggregate.setOption(SQLAggregateOption.DISTINCT);
		}
		if (orders != null) {
			SQLOrderBy orderBy = new SQLOrderBy();
			for (Order order : orders) {
				SQLSelectOrderByItem orderItem  = new SQLSelectOrderByItem(order.getItem().toExpression());
				orderItem.setType(order.getSortOrder());
				orderBy.addItem(orderItem);
			}
			aggregate.putAttribute(ItemFuncKeyWord.ORDER_BY, orderBy);
		}
		for (Item arg : args) {
			aggregate.addArgument(arg.toExpression());
		}
		if (seperator != null) {
			SQLCharExpr sep = new SQLCharExpr(seperator);
			aggregate.putAttribute(ItemFuncKeyWord.SEPARATOR, sep);
		}
		return aggregate;
	}

	@Override
	protected Item cloneStruct(boolean forCalculate, List<Item> calArgs, boolean isPushDown, List<Field> fields) {
		if (!forCalculate) {
			List<Item> argList = cloneStructList(args);
			return new ItemFuncGroupConcat(argList, has_with_distinct(), this.orders, this.seperator,
					false, null);
		} else {
			return new ItemFuncGroupConcat(calArgs, has_with_distinct(), this.orders, this.seperator, isPushDown,
					fields);
		}
	}

}
