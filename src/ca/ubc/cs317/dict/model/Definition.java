package ca.ubc.cs317.dict.model;

import java.util.Objects;

/**
 * Created by Jonatan on 2017-09-09.
 */
public class Definition {

    private String word;
    private String databaseName;
    private String definition;

    public Definition(String word, String database) {
        this.word = word;
        this.databaseName = database;
    }

    public String getWord() {
        return word;
    }

    public String getDatabaseName() {
        return databaseName;
    }

    public String getDefinition() {
        return definition;
    }

    public void setDefinition(String definition) {
        this.definition = definition.trim();
    }

    public void appendDefinition(String definition) {
        if (this.definition == null)
            this.definition = definition;
        else if (definition != null)
            this.definition += System.lineSeparator() + definition;
    }

    @Override
    public String toString() {
        return "('" + word + '\'' +
                "@'" + databaseName + '\'' +
                ": '" + definition + '\'';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Definition that = (Definition) o;
        return word.equals(that.word) && databaseName.equals(that.databaseName) && Objects.equals(definition, that.definition);
    }

    @Override
    public int hashCode() {
        return Objects.hash(word, databaseName, definition);
    }
}
