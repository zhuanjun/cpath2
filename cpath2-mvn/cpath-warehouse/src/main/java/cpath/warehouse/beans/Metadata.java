package cpath.warehouse.beans;

// imports
import javax.persistence.*;
import org.hibernate.search.annotations.Indexed;

import cpath.warehouse.CPathWarehouse;

/**
 * Data Provider Metadata.
 */
@Entity
@Table(name="metadata")
@NamedQueries({
		@NamedQuery(name="cpath.warehouse.beans.providerByIdentifier",
					query="from Metadata as metadata where upper(metadata.identifier) = upper(:identifier)")
})
@Indexed(index=CPathWarehouse.SEARCH_INDEX_NAME)
public final class Metadata {

	@Id
	@Column(name="provider_id")
	@GeneratedValue(strategy=GenerationType.AUTO)
	private Integer id;
	@Column(nullable=false, unique=true)
    private String identifier;
	@Column(nullable=false)
    private String name;
	@Column(nullable=false)
    private Float version;
	@Column(nullable=false)
	private Float persistedVersion; // version that was persisted
	@Column(nullable=false)
    private String releaseDate;
	@Column(nullable=false)
    private String urlToPathwayData;
	@Lob
	@Column(nullable=false)
    private byte[] icon;
	@Column(nullable=false)
    private Boolean isPSI;

	/**
	 * Default Constructor.
	 */
	public Metadata() {}

    /**
     * Create a Metadata obj with the specified properties;
     *
     * @param identifier String (string used in web service calls)
     * @param name String
     * @param version Float
     * @param releaseDate String
     * @param urlToPathwayData String
     * @param icon byte[]
     * @param isPSI Boolean
     */
    public Metadata(final String identifier, final String name, final Float version, final String releaseDate,
                    final String urlToPathwayData, final byte[] icon, final Boolean isPSI) {

        if (identifier == null) {
            throw new IllegalArgumentException("identifier must not be null");
        }
        this.identifier = identifier;

        if (name == null) {
            throw new IllegalArgumentException("name must not be null");
        }
        this.name = name;

        if (version == null) {
            throw new IllegalArgumentException("version must not be null");
        }
        this.version = version;
		this.persistedVersion = 0.0f;

        if (releaseDate == null) {
            throw new IllegalArgumentException("release data must not be null");
        }
        this.releaseDate = releaseDate;

        if (urlToPathwayData == null) {
            throw new IllegalArgumentException("URL to pathway data must not be null");
        }
        this.urlToPathwayData = urlToPathwayData;

        if (icon == null) {
            throw new IllegalArgumentException("icon must not be null");
        }
        this.icon = icon;

        if (isPSI == null) {
            throw new IllegalArgumentException("isPSI must not be null");
        }
        this.isPSI = isPSI;
    }

	public void setId(Integer id) {
		this.id = id;
	}
    public Integer getId() { return id; }

	public void setIdentifier(String identifier) {
		this.identifier = identifier;
	}
    public String getIdentifier() { return identifier; }

	public void setName(String name) {
		this.name = name;
	}
    public String getName() { return name; }

	public void setVersion(Float version) {
		this.version = version;
	}
    public Float getVersion() { return version; }

	public void setPersistedVersion(Float persistedVersion) {
		this.persistedVersion = persistedVersion;
	}
    public Float getPersistedVersion() { return persistedVersion; }

	public void setReleaseDate(String releaseData) {
		this.releaseDate = releaseDate;
	}
    public String getReleaseDate() { return releaseDate; }

	public void setURLToPathwayData(String urlToPathwayData) {
		this.urlToPathwayData = urlToPathwayData;
	}
    public String getURLToPathwayData() { return urlToPathwayData; }

	public void setIcon(byte[] icon) {
		this.icon = icon;
	}
    public byte[] getIcon() { return icon; }

	public void setIsPSI(Boolean isPSI) {
		this.isPSI = isPSI;
	}
    public Boolean isPSI() { return isPSI; }

    @Override
    public String toString() {
        return identifier;
    }
}
