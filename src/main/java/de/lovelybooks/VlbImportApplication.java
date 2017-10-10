package de.lovelybooks;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import de.lovelybooks.etl.service.VlbImportService;

@SpringBootApplication
public class VlbImportApplication implements CommandLineRunner {

    @Autowired
    private VlbImportService vlbImportService;

	public static void main(String[] args) {
		SpringApplication.run(VlbImportApplication.class, args);
	}

    @Override
    public void run(String... args) throws Exception {
        vlbImportService.start();
    }
}
