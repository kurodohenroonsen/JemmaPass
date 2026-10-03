import Foundation

// MARK: - JAllergy & JReaction

public struct JReaction: Codable, Sendable, Equatable {
    public var m: String?
    public var s: String?

    public init(m: String? = nil, s: String? = nil) {
        self.m = m
        self.s = s
    }
}

public struct JAllergy: Codable, Sendable, Equatable {
    public var c: String?
    public var s: String?
    public var st: String?
    public var d: String?
    public var m: String?
    public var d_display: String?
    public var on: String?
    public var rxns: [JReaction]

    enum CodingKeys: String, CodingKey {
        case c, s, st, d, m, d_display, on, rxns
    }

    public init(
        c: String? = nil,
        s: String? = nil,
        st: String? = nil,
        d: String? = nil,
        m: String? = nil,
        d_display: String? = nil,
        on: String? = nil,
        rxns: [JReaction] = []
    ) {
        self.c = c
        self.s = s
        self.st = st
        self.d = d
        self.m = m
        self.d_display = d_display
        self.on = on
        self.rxns = rxns
    }

    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        self.c = try container.decodeIfPresent(String.self, forKey: .c)
        self.s = try container.decodeIfPresent(String.self, forKey: .s)
        self.st = try container.decodeIfPresent(String.self, forKey: .st)
        self.d = try container.decodeIfPresent(String.self, forKey: .d)
        self.m = try container.decodeIfPresent(String.self, forKey: .m)
        self.d_display = try container.decodeIfPresent(String.self, forKey: .d_display)
        self.on = try container.decodeIfPresent(String.self, forKey: .on)
        self.rxns = try container.decodeIfPresent([JReaction].self, forKey: .rxns) ?? []
    }

    public func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encodeIfPresent(c, forKey: .c)
        try container.encodeIfPresent(s, forKey: .s)
        try container.encodeIfPresent(st, forKey: .st)
        try container.encodeIfPresent(d, forKey: .d)
        try container.encodeIfPresent(m, forKey: .m)
        try container.encodeIfPresent(d_display, forKey: .d_display)
        try container.encodeIfPresent(on, forKey: .on)
        if !rxns.isEmpty { try container.encode(rxns, forKey: .rxns) }
    }
}

// MARK: - JMedication

public struct JMedication: Codable, Sendable, Equatable {
    public var c: String?
    public var t: String?
    public var r: String?
    public var v: String?
    public var u: String?
    public var rs: String?
    public var rc: String?
    public var d_display: String?
    public var cs: String?
    public var ms: String?
    public var eff: String?
    public var effar: String?

    public init(
        c: String? = nil,
        t: String? = nil,
        r: String? = nil,
        v: String? = nil,
        u: String? = nil,
        rs: String? = nil,
        rc: String? = nil,
        d_display: String? = nil,
        cs: String? = nil,
        ms: String? = nil,
        eff: String? = nil,
        effar: String? = nil
    ) {
        self.c = c
        self.t = t
        self.r = r
        self.v = v
        self.u = u
        self.rs = rs
        self.rc = rc
        self.d_display = d_display
        self.cs = cs
        self.ms = ms
        self.eff = eff
        self.effar = effar
    }
}

// MARK: - JCondition

public struct JCondition: Codable, Sendable, Equatable {
    public var c: String?
    public var s: String?
    public var st: String?
    public var d: String?
    public var rs: String?
    public var rc: String?
    public var d_display: String?
    public var dt: String?
    public var cs: String?

    public init(
        c: String? = nil,
        s: String? = nil,
        st: String? = nil,
        d: String? = nil,
        rs: String? = nil,
        rc: String? = nil,
        d_display: String? = nil,
        dt: String? = nil,
        cs: String? = nil
    ) {
        self.c = c
        self.s = s
        self.st = st
        self.d = d
        self.rs = rs
        self.rc = rc
        self.d_display = d_display
        self.dt = dt
        self.cs = cs
    }
}

// MARK: - JEntryGeneric

public struct JEntryGeneric: Codable, Sendable, Equatable {
    public var c: String?
    public var d: String?
    public var d_display: String?
    public var dt: String?
    public var cs: String?
    public var st: String?
    public var dn: Int?
    public var v: String?
    public var u: String?
    public var ip: String?
    public var rr: String?
    public var vc: String?
    public var vcs: String?
    public var ct: String?
    public var ab: String?
    public var sv: String?

    public init(
        c: String? = nil,
        d: String? = nil,
        d_display: String? = nil,
        dt: String? = nil,
        cs: String? = nil,
        st: String? = nil,
        dn: Int? = nil,
        v: String? = nil,
        u: String? = nil,
        ip: String? = nil,
        rr: String? = nil,
        vc: String? = nil,
        vcs: String? = nil,
        ct: String? = nil,
        ab: String? = nil,
        sv: String? = nil
    ) {
        self.c = c
        self.d = d
        self.d_display = d_display
        self.dt = dt
        self.cs = cs
        self.st = st
        self.dn = dn
        self.v = v
        self.u = u
        self.ip = ip
        self.rr = rr
        self.vc = vc
        self.vcs = vcs
        self.ct = ct
        self.ab = ab
        self.sv = sv
    }
}
