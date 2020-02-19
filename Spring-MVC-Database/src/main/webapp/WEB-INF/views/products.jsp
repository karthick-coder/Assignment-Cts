<%@ page language="java" contentType="text/html; charset=UTF-8"
    pageEncoding="UTF-8"%>
  <%@ taglib  uri="http://java.sun.com/jstl/core_rt" prefix="c" %>  
<!DOCTYPE html>
<html>
<head>
<meta charset="UTF-8">
<title>Insert title here</title>
</head>
<body>

<h1>List of Products</h1>

<h2>${prods}</h2>


<c:forEach items="${prods}"  var="prod">
	<h2> ${prod }</h2>
</c:forEach>

<hr/>

<c:forEach var ="product"  items="${listProds}">
	<h2>${product }</h2>
</c:forEach>


</body>
</html>