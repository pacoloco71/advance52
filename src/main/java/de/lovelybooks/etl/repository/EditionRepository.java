package de.lovelybooks.etl.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import de.lovelybooks.entity.Edition;

@Repository
public interface EditionRepository extends JpaRepository<Edition, Long> {

    Edition findFirstByIsbn13(String isbn13);

}
