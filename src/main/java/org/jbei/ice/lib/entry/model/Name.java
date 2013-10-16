package org.jbei.ice.lib.entry.model;

import javax.persistence.*;
import javax.xml.bind.annotation.XmlTransient;

import org.jbei.ice.lib.dao.IModel;

import org.hibernate.search.annotations.Analyze;
import org.hibernate.search.annotations.Boost;
import org.hibernate.search.annotations.ContainedIn;
import org.hibernate.search.annotations.Field;
import org.hibernate.search.annotations.Fields;
import org.hibernate.search.annotations.Store;

/**
 * Store the friendly name for an {@link org.jbei.ice.lib.entry.model.Entry}.
 *
 * @author Timothy Ham, Zinovii Dmytriv, Hector Plahar
 */
@Entity
@Table(name = "names")
@SequenceGenerator(name = "sequence", sequenceName = "names_id_seq", allocationSize = 1)
public class Name implements IModel {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO, generator = "sequence")
    private long id;

    @Column(name = "name", length = 127, nullable = false)
    @Fields({
                    @Field(store = Store.YES, boost = @Boost(2f)),
                    @Field(name = "name_forSort", analyze = Analyze.NO, store = Store.YES)
            })
    private String name;

    @ContainedIn
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "entries_id", nullable = false, unique = false)
    private Entry entry;

    public Name() {
    }

    public Name(String name, Entry entry) {
        this.name = name;
        this.entry = entry;
    }

    public void setId(long id) {
        this.id = id;
    }

    @XmlTransient
    public long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @XmlTransient
    public Entry getEntry() {
        return entry;
    }

    public void setEntry(Entry entry) {
        this.entry = entry;
    }

    @Override
    public String toString() {
        return name;
    }
}