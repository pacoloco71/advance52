package de.lovelybooks.etl.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import de.lovelybooks.entity.Book;

@Repository
public interface BookRepository extends JpaRepository<Book, Long> {

}
