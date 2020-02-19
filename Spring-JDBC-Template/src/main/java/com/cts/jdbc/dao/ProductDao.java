package com.cts.jdbc.dao;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.cts.jdbc.model.Product;

@Repository
public class ProductDao {

	@Autowired
	private JdbcTemplate jt;

	public List<Product> findAll() {

		List<Product> products = jt.queryForObject("select * from product", new RowMapperDemo());

		return Optional.ofNullable(products).orElse(null);
	}

	public List<Product> findAll_V1() {

		List<Product> products = jt.query("select * from product", new ResultSetExtractorDemo());

		return Optional.ofNullable(products).orElse(null);
	}

	public List<Product> findByName(String productName) {
		List<Product> products = jt.query("select * from product where product_name like ?",
				new ResultSetExtractorDemoByName(), "%" + productName + "%");
		return Optional.ofNullable(products).orElse(null);
	}

	public Product findById(int prodId) {
		return jt.query("select * from product where product_id=?", new ResultSetExtractorDemoById(), prodId);
	}

	public int saveProduct(Product product) {
		 
		return jt.update("insert into product values(?,?,?)", product.getProductId(),
				product.getProductName(), product.getPrice());
	}

	public List<Product> findByPriceRange(int i, int j) {
		 
		return jt.query("select * from product where price between ? and ? ",
				new ResultSetExtractorDemoByPriceRange(), i, j);
	}

	@SuppressWarnings("unchecked")
	public List<Product> findBetweenId(int i, int j) {
		List<Product> products = (List<Product>) jt.query("select * from product where product_id between ? and ?",
				new ResultSetExtractorDemoByRangeId(), i, j);
		return Optional.ofNullable(products).orElse(null);
	}

	public int deleteById(int i) {

		return jt.update("delete from product where product_id= ? ", i);
	}

	public int editData(String nm, int i) {
		return jt.update("update product set product_name= ? where product_id= ? ", nm, i);
	}

}

class ResultSetExtractorDemo implements ResultSetExtractor<List<Product>> {

	public List<Product> extractData(ResultSet rs) throws SQLException, DataAccessException {
		List<Product> products = new ArrayList<Product>();

		while (rs.next()) {
			Product product = new Product();
			product.setProductId(rs.getInt("product_id"));
			product.setProductName(rs.getString("product_name"));
			product.setPrice(rs.getDouble("price"));
			products.add(product);
		}
		return products;
	}

}

class ResultSetExtractorDemoByRangeId implements ResultSetExtractor {

	public List<Product> extractData(ResultSet rs) throws SQLException, DataAccessException {
		List<Product> products = new ArrayList<Product>();
		while (rs.next()) {
			Product prod = new Product();
			prod.setProductId(rs.getInt("product_id"));
			prod.setProductName(rs.getString("product_name"));
			prod.setPrice(rs.getDouble("price"));
			products.add(prod);
		}
		return products;
	}

}

class ResultSetExtractorDemoByPriceRange implements ResultSetExtractor<List<Product>> {

	public List<Product> extractData(ResultSet rs) throws SQLException, DataAccessException {
		List<Product> products = new ArrayList<Product>();
		while (rs.next()) {
			Product prod = new Product();
			prod.setProductId(rs.getInt("product_id"));
			prod.setProductName(rs.getString("product_name"));
			prod.setPrice(rs.getDouble("price"));
			products.add(prod);
		}
		return products;
	}

}

class ResultSetExtractorDemoByName implements ResultSetExtractor<List<Product>> {

	public List<Product> extractData(ResultSet rs) throws SQLException, DataAccessException {
		List<Product> products = new ArrayList<Product>();
		while (rs.next()) {
			Product prod = new Product();
			prod.setProductId(rs.getInt("product_id"));
			prod.setProductName(rs.getString("product_name"));
			prod.setPrice(rs.getDouble("price"));
			products.add(prod);
		}
		return products;
	}

}

class ResultSetExtractorDemoById implements ResultSetExtractor<Product> {

	public Product extractData(ResultSet rs) throws SQLException, DataAccessException {

		Product prod = null;
		if (rs.next()) {
			prod = new Product();
			prod.setProductId(rs.getInt("product_id"));
			prod.setProductName(rs.getString("product_name"));
			prod.setPrice(rs.getDouble("price"));
		}
		return prod;
	}

}

class RowMapperDemo implements RowMapper<List<Product>> {

	List<Product> products = new ArrayList<Product>();

	public List<Product> mapRow(ResultSet rs, int rowNum) throws SQLException {

		while (rs.next()) {
			Product prod = new Product();
			prod.setProductId(rs.getInt("product_id"));
			prod.setProductName(rs.getString("product_name"));
			prod.setPrice(rs.getDouble("price"));
			products.add(prod);
		}

		return products;
	}
}
