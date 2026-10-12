package com.forgegate.controller;
import org.springframework.jdbc.core.JdbcTemplate; import org.springframework.web.bind.annotation.*; import java.util.List; import java.util.Map;
@RestController @RequestMapping("/api/catalog") public class CatalogController { private final JdbcTemplate db; public CatalogController(JdbcTemplate db){this.db=db;} @GetMapping("/resources") public List<Map<String,Object>> resources(){return db.queryForList("SELECT id,name,type,sensitivity,owner_department FROM resources WHERE is_active=true ORDER BY name");} }
