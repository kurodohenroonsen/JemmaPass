import Foundation

// MARK: - JPatient

public struct JPatient: Codable, Sendable, Equatable {
    public var gn: String?
    public var fn: String?
    public var gs: String?
    public var bd: String?
    public var nat: String?
    public var bt: String?
    public var adr: String?
    public var tel: String?
    public var eml: String?
    public var idn: String?
    public var ids: [JIdentifier]
    public var adrs: [JAddress]
    public var tels: [JTelecom]
    public var gp: String?
    public var lang: String?
    public var ct: [JContact]

    enum CodingKeys: String, CodingKey {
        case gn, fn, gs, bd, nat, bt
        case adr, tel, eml, idn
        case ids, adrs, tels, gp, lang, ct
    }

    public init(
        gn: String? = nil,
        fn: String? = nil,
        gs: String? = nil,
        bd: String? = nil,
        nat: String? = nil,
        bt: String? = nil,
        adr: String? = nil,
        tel: String? = nil,
        eml: String? = nil,
        idn: String? = nil,
        ids: [JIdentifier] = [],
        adrs: [JAddress] = [],
        tels: [JTelecom] = [],
        gp: String? = nil,
        lang: String? = nil,
        ct: [JContact] = []
    ) {
        self.gn = gn
        self.fn = fn
        self.gs = gs
        self.bd = bd
        self.nat = nat
        self.bt = bt
        self.adr = adr
        self.tel = tel
        self.eml = eml
        self.idn = idn
        self.ids = ids
        self.adrs = adrs
        self.tels = tels
        self.gp = gp
        self.lang = lang
        self.ct = ct
    }

    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        self.gn = try container.decodeIfPresent(String.self, forKey: .gn)
        self.fn = try container.decodeIfPresent(String.self, forKey: .fn)
        self.gs = try container.decodeIfPresent(String.self, forKey: .gs)
        self.bd = try container.decodeIfPresent(String.self, forKey: .bd)
        self.nat = try container.decodeIfPresent(String.self, forKey: .nat)
        self.bt = try container.decodeIfPresent(String.self, forKey: .bt)
        self.adr = try container.decodeIfPresent(String.self, forKey: .adr)
        self.tel = try container.decodeIfPresent(String.self, forKey: .tel)
        self.eml = try container.decodeIfPresent(String.self, forKey: .eml)
        self.idn = try container.decodeIfPresent(String.self, forKey: .idn)
        self.ids = try container.decodeIfPresent([JIdentifier].self, forKey: .ids) ?? []
        self.adrs = try container.decodeIfPresent([JAddress].self, forKey: .adrs) ?? []
        self.tels = try container.decodeIfPresent([JTelecom].self, forKey: .tels) ?? []
        self.gp = try container.decodeIfPresent(String.self, forKey: .gp)
        self.lang = try container.decodeIfPresent(String.self, forKey: .lang)
        self.ct = try container.decodeIfPresent([JContact].self, forKey: .ct) ?? []
    }

    public func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encodeIfPresent(gn, forKey: .gn)
        try container.encodeIfPresent(fn, forKey: .fn)
        try container.encodeIfPresent(gs, forKey: .gs)
        try container.encodeIfPresent(bd, forKey: .bd)
        try container.encodeIfPresent(nat, forKey: .nat)
        try container.encodeIfPresent(bt, forKey: .bt)
        try container.encodeIfPresent(adr, forKey: .adr)
        try container.encodeIfPresent(tel, forKey: .tel)
        try container.encodeIfPresent(eml, forKey: .eml)
        try container.encodeIfPresent(idn, forKey: .idn)
        if !ids.isEmpty { try container.encode(ids, forKey: .ids) }
        if !adrs.isEmpty { try container.encode(adrs, forKey: .adrs) }
        if !tels.isEmpty { try container.encode(tels, forKey: .tels) }
        try container.encodeIfPresent(gp, forKey: .gp)
        try container.encodeIfPresent(lang, forKey: .lang)
        if !ct.isEmpty { try container.encode(ct, forKey: .ct) }
    }
}

// MARK: - JContact

public struct JContact: Codable, Sendable, Equatable {
    public var n: String?
    public var r: String?
    public var p: String?
    public var e: String?
    public var adr: String?

    enum CodingKeys: String, CodingKey {
        case n, r, p, e, adr
    }

    public init(
        n: String? = nil,
        r: String? = nil,
        p: String? = nil,
        e: String? = nil,
        adr: String? = nil
    ) {
        self.n = n
        self.r = r
        self.p = p
        self.e = e
        self.adr = adr
    }
}

// MARK: - JIdentifier

public struct JIdentifier: Codable, Sendable, Equatable {
    public var system: String?
    public var value: String?

    enum CodingKeys: String, CodingKey {
        case system = "s"
        case value = "v"
    }

    public init(system: String? = nil, value: String? = nil) {
        self.system = system
        self.value = value
    }
}

// MARK: - JAddress

public struct JAddress: Codable, Sendable, Equatable {
    public var use: String?
    public var line: String?
    public var city: String?
    public var postalCode: String?
    public var country: String?

    public init(
        use: String? = nil,
        line: String? = nil,
        city: String? = nil,
        postalCode: String? = nil,
        country: String? = nil
    ) {
        self.use = use
        self.line = line
        self.city = city
        self.postalCode = postalCode
        self.country = country
    }
}

// MARK: - JTelecom

public struct JTelecom: Codable, Sendable, Equatable {
    public var system: String?
    public var value: String?
    public var use: String?

    public init(
        system: String? = nil,
        value: String? = nil,
        use: String? = nil
    ) {
        self.system = system
        self.value = value
        self.use = use
    }
}
