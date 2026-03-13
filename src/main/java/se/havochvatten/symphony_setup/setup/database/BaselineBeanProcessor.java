package se.havochvatten.symphony_setup.setup.database;

import org.apache.commons.dbutils.BeanProcessor;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Map;

public class BaselineBeanProcessor extends BeanProcessor {

    public BaselineBeanProcessor(Map<String, String> columnToPropertyOverrides) {
        super(columnToPropertyOverrides);
    }

    @Override
    protected Object processColumn(ResultSet resultSet, int index, Class<?> propType) throws SQLException {
        if (propType.equals(LocalDate.class)) {
            Object retval = resultSet.getObject(index);
            return ((Date) retval).toLocalDate();
        } else {
            return super.processColumn(resultSet, index, propType);
        }
    }
}
