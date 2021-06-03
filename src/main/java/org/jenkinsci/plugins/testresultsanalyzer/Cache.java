package org.jenkinsci.plugins.testresultsanalyzer;
import hudson.model.Job;
import net.sf.json.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Cache {
    private final Job job;
    private final File file;
    private final String pathName;

    public Cache(Job job) {
        this.job = job;
        this.pathName = "work/jobs/%s/cache.json";
        file = new File(String.format(this.pathName, job.getName()));
        tryCreateFile();
    }

    private void createFile() throws IOException {
        if (!file.exists()) {
            boolean isCreated = file.createNewFile();
            if (isCreated) {
                System.out.println("Cache file was created successfully");
            } else {
                System.out.println("Cache file has already been created");
            }
        }
    }

    private void tryCreateFile()  {
        try {
            createFile();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public boolean isEmpty() {
        return file.length() == 0;
    }

    public boolean isNeedsUpdate() throws IOException, ParseException {
        FileReader fileReader = new FileReader(file);
        JSONParser jsonParser = new JSONParser();
        org.json.simple.JSONObject jsonObject = (org.json.simple.JSONObject) jsonParser.parse(fileReader);
        fileReader.close();
        long id = (long)jsonObject.get("lastBuild");
        return job.getLastBuild().getNumber() != id;
    }

    public String getData() throws IOException {
        Path path = Paths.get(String.format(this.pathName, job.getName()));
        return new String(Files.readAllBytes(path));
    }

    public void save(JSONObject builds) throws IOException {
        FileWriter fileWriter = new FileWriter(file);
        try(BufferedWriter bufferedWriter = new BufferedWriter(fileWriter)) {
            bufferedWriter.write(String.valueOf(builds));
        } catch (IOException e) {
            e.printStackTrace();
        }
        fileWriter.close();
    }

    public void delete() {
        if (file.delete()) {
            System.out.println("Cache was deleted");
        }
    }
}
