import Foundation

// MARK: - JemmaProfileJ (Schema `_j 1.2` SHORT format)

public struct JemmaProfileJ: Codable, Sendable, Equatable {
    public var schemaVersion: String?
    public var sid: String?
    public var p: JPatient?
    public var al: [JAllergy]
    public var md: [JMedication]
    public var cn: [JCondition]
    public var ph: [JEntryGeneric]
    public var im: [JEntryGeneric]
    public var pr: [JEntryGeneric]
    public var dv: [JEntryGeneric]
    public var fs: [JEntryGeneric]
    public var pg: [JEntryGeneric]
    public var rs: [JEntryGeneric]
    public var ad: [JEntryGeneric]
    public var cs: [JEntryGeneric]
    public var gl: [JEntryGeneric]
    public var en: [JEntryGeneric]
    public var oc: [JEntryGeneric]
    public var pv: [JEntryGeneric]

    enum CodingKeys: String, CodingKey {
        case schemaVersion = "_j"
        case sid
        case p
        case al, md, cn, ph, im, pr, dv, fs, pg, rs
        case ad, cs, gl, en, oc, pv
    }

    public init(
        schemaVersion: String? = "1.2",
        sid: String? = nil,
        p: JPatient? = nil,
        al: [JAllergy] = [],
        md: [JMedication] = [],
        cn: [JCondition] = [],
        ph: [JEntryGeneric] = [],
        im: [JEntryGeneric] = [],
        pr: [JEntryGeneric] = [],
        dv: [JEntryGeneric] = [],
        fs: [JEntryGeneric] = [],
        pg: [JEntryGeneric] = [],
        rs: [JEntryGeneric] = [],
        ad: [JEntryGeneric] = [],
        cs: [JEntryGeneric] = [],
        gl: [JEntryGeneric] = [],
        en: [JEntryGeneric] = [],
        oc: [JEntryGeneric] = [],
        pv: [JEntryGeneric] = []
    ) {
        self.schemaVersion = schemaVersion
        self.sid = sid
        self.p = p
        self.al = al
        self.md = md
        self.cn = cn
        self.ph = ph
        self.im = im
        self.pr = pr
        self.dv = dv
        self.fs = fs
        self.pg = pg
        self.rs = rs
        self.ad = ad
        self.cs = cs
        self.gl = gl
        self.en = en
        self.oc = oc
        self.pv = pv
    }

    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        self.schemaVersion = try container.decodeIfPresent(String.self, forKey: .schemaVersion)
        self.sid = try container.decodeIfPresent(String.self, forKey: .sid)
        self.p = try container.decodeIfPresent(JPatient.self, forKey: .p)
        self.al = try container.decodeIfPresent([JAllergy].self, forKey: .al) ?? []
        self.md = try container.decodeIfPresent([JMedication].self, forKey: .md) ?? []
        self.cn = try container.decodeIfPresent([JCondition].self, forKey: .cn) ?? []
        self.ph = try container.decodeIfPresent([JEntryGeneric].self, forKey: .ph) ?? []
        self.im = try container.decodeIfPresent([JEntryGeneric].self, forKey: .im) ?? []
        self.pr = try container.decodeIfPresent([JEntryGeneric].self, forKey: .pr) ?? []
        self.dv = try container.decodeIfPresent([JEntryGeneric].self, forKey: .dv) ?? []
        self.fs = try container.decodeIfPresent([JEntryGeneric].self, forKey: .fs) ?? []
        self.pg = try container.decodeIfPresent([JEntryGeneric].self, forKey: .pg) ?? []
        self.rs = try container.decodeIfPresent([JEntryGeneric].self, forKey: .rs) ?? []
        self.ad = try container.decodeIfPresent([JEntryGeneric].self, forKey: .ad) ?? []
        self.cs = try container.decodeIfPresent([JEntryGeneric].self, forKey: .cs) ?? []
        self.gl = try container.decodeIfPresent([JEntryGeneric].self, forKey: .gl) ?? []
        self.en = try container.decodeIfPresent([JEntryGeneric].self, forKey: .en) ?? []
        self.oc = try container.decodeIfPresent([JEntryGeneric].self, forKey: .oc) ?? []
        self.pv = try container.decodeIfPresent([JEntryGeneric].self, forKey: .pv) ?? []
    }

    public func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encodeIfPresent(schemaVersion, forKey: .schemaVersion)
        try container.encodeIfPresent(sid, forKey: .sid)
        try container.encodeIfPresent(p, forKey: .p)
        if !al.isEmpty { try container.encode(al, forKey: .al) }
        if !md.isEmpty { try container.encode(md, forKey: .md) }
        if !cn.isEmpty { try container.encode(cn, forKey: .cn) }
        if !ph.isEmpty { try container.encode(ph, forKey: .ph) }
        if !im.isEmpty { try container.encode(im, forKey: .im) }
        if !pr.isEmpty { try container.encode(pr, forKey: .pr) }
        if !dv.isEmpty { try container.encode(dv, forKey: .dv) }
        if !fs.isEmpty { try container.encode(fs, forKey: .fs) }
        if !pg.isEmpty { try container.encode(pg, forKey: .pg) }
        if !rs.isEmpty { try container.encode(rs, forKey: .rs) }
        if !ad.isEmpty { try container.encode(ad, forKey: .ad) }
        if !cs.isEmpty { try container.encode(cs, forKey: .cs) }
        if !gl.isEmpty { try container.encode(gl, forKey: .gl) }
        if !en.isEmpty { try container.encode(en, forKey: .en) }
        if !oc.isEmpty { try container.encode(oc, forKey: .oc) }
        if !pv.isEmpty { try container.encode(pv, forKey: .pv) }
    }
}
