/**
 * JemmaPassChrome/core/fhir_codec.ts
 *
 * Décodeur de Bundle HL7 FHIR R4 IPS vers le modèle `_j 1.2` (JemmaProfileJ).
 * Complémentaire de fhir_builder.ts.
 * Strictement sans accès DOM ni chrome.*
 */

import type {
  JemmaProfileJ,
  JPatient,
  JContact,
  JAllergy,
  JMedication,
  JCondition,
  JEntryGeneric,
  FhirBundle,
  FhirEntry,
  FhirPatient,
} from "./types.ts";
import { LOINC_ABO_RH, normalizeBloodGroup, labelFromSnomedCode } from "./blood_group.ts";

export function parseFhirBundle(rawText: string): JemmaProfileJ {
  const bundle: FhirBundle = JSON.parse(rawText);
  if (bundle.resourceType !== "Bundle") {
    throw new Error("Invalid FHIR JSON: resourceType is not Bundle");
  }

  // Sid dérivé du Bundle identifier ou d'un subject
  let sid = "imported_profile";
  if (bundle.identifier?.value) {
    // urn:uuid:... ou sid|Bundle
    const val = bundle.identifier.value;
    const match = val.match(/urn:uuid:(.+)/);
    if (match) {
      sid = `fhir_${match[1].slice(0, 8)}`;
    }
  }

  const entries: FhirEntry[] = bundle.entry || [];
  const resources: any[] = entries.map(e => e.resource).filter(Boolean);

  const profile: JemmaProfileJ = {
    _j: "1.2",
    sid,
    p: {},
    al: [],
    md: [],
    cn: [],
    ph: [],
    im: [],
    pr: [],
    dv: [],
    fs: [],
    pg: [],
    rs: [],
    ad: [],
    cs: [],
    gl: [],
    en: [],
    oc: [],
    pv: [],
  };

  // Map des ressources par fullUrl et par ID
  const resByUrl = new Map<string, any>();
  const resById = new Map<string, any>();
  for (const entry of entries) {
    if (entry.fullUrl && entry.resource) {
      resByUrl.set(entry.fullUrl, entry.resource);
    }
    if (entry.resource?.id) {
      resById.set(entry.resource.id, entry.resource);
    }
  }

  // 1. Patient
  const patientRes: FhirPatient | undefined = resources.find(r => r.resourceType === "Patient");
  if (patientRes) {
    const p: JPatient = {
      ct: [],
      ids: [],
      adrs: [],
      tels: [],
    };

    if (patientRes.name && patientRes.name.length > 0) {
      const n = patientRes.name[0];
      if (n.given && n.given.length > 0) p.gn = n.given.join(" ");
      if (n.family) p.fn = n.family;
    }

    if (patientRes.gender) {
      const g = patientRes.gender.toLowerCase();
      p.gs = g === "male" ? "M" : g === "female" ? "F" : g === "other" ? "O" : "U";
    }

    if (patientRes.birthDate) {
      p.bd = patientRes.birthDate;
    }

    if (patientRes.address && patientRes.address.length > 0) {
      p.adr = patientRes.address[0].text || patientRes.address[0].line?.join(", ");
    }

    if (patientRes.telecom) {
      for (const t of patientRes.telecom) {
        if (t.system === "phone" && !p.tel) p.tel = t.value;
        if (t.system === "email" && !p.eml) p.eml = t.value;
      }
    }

    if (patientRes.communication && patientRes.communication.length > 0) {
      p.lang = patientRes.communication[0].language?.coding?.[0]?.code;
    }

    // Contacts d'urgence
    if (patientRes.contact) {
      for (const c of patientRes.contact) {
        const jc: JContact = {};
        if (c.name?.text) jc.n = c.name.text;
        if (c.address?.text) jc.adr = c.address.text;
        if (c.telecom) {
          for (const t of c.telecom) {
            if (t.system === "phone" && !jc.p) jc.p = t.value;
            if (t.system === "email" && !jc.e) jc.e = t.value;
          }
        }
        if (c.relationship && c.relationship.length > 0) {
          const rel = c.relationship[0];
          const coding = rel.coding?.[0];
          jc.r = coding?.code || rel.text;
        }
        if (jc.n || jc.p || jc.e || jc.adr || jc.r) {
          p.ct!.push(jc);
        }
      }
    }

    profile.p = p;
  }

  // 2. Allergies
  const allergyResources = resources.filter(r => r.resourceType === "AllergyIntolerance");
  for (const a of allergyResources) {
    const coding = a.code?.coding?.[0];
    const critCode = a.criticality?.toLowerCase();
    const crit = critCode === "high" ? "H" : critCode === "low" ? "L" : "U";
    const clinStatus = a.clinicalStatus?.coding?.[0]?.code;
    const st = clinStatus === "inactive" ? "I" : clinStatus === "resolved" ? "R" : "A";

    profile.al!.push({
      c: coding?.code,
      d_display: a.code?.text || coding?.display,
      cs: coding?.system,
      s: crit,
      st,
      cat: a.category?.[0],
    });
  }

  // 3. Medications
  const medStatements = resources.filter(r => r.resourceType === "MedicationStatement");
  for (const ms of medStatements) {
    const medRef = ms.medicationReference?.reference;
    const medRes = (medRef && resByUrl.get(medRef)) || (medRef && resById.get(medRef.replace("Medication/", "")));
    const medCoding = medRes?.code?.coding?.[0];
    const displayLabel = medRes?.code?.text || medCoding?.display;

    const jm: JMedication = {
      c: medCoding?.code,
      d_display: displayLabel,
      cs: medCoding?.system,
      ms: ms.status,
    };

    if (ms.dosage && ms.dosage.length > 0) {
      const dosage = ms.dosage[0];
      jm.t = dosage.text;
      const routeCode = dosage.route?.coding?.[0]?.code;
      if (routeCode === "26643006") jm.r = "O";
      else if (routeCode === "6064005") jm.r = "T";
      else if (routeCode === "34206005") jm.r = "S";
      else if (routeCode === "447694001") jm.r = "H";
      else if (dosage.route?.text === "Injection") jm.r = "I";

      const doseQuantity = dosage.doseAndRate?.[0]?.doseQuantity;
      if (doseQuantity) {
        if (doseQuantity.value != null) jm.v = String(doseQuantity.value);
        if (doseQuantity.unit) jm.u = doseQuantity.unit;
      }
    }

    if (ms.effectivePeriod) {
      jm.eff = `${ms.effectivePeriod.start || ""}/${ms.effectivePeriod.end || ""}`;
    } else if (ms.effectiveDateTime) {
      jm.eff = ms.effectiveDateTime;
    }

    profile.md!.push(jm);
  }

  // 4. Conditions (Problems, Past Illness, Functional)
  const conditionResources = resources.filter(r => r.resourceType === "Condition");
  for (const c of conditionResources) {
    const coding = c.code?.coding?.[0];
    const display = c.code?.text || coding?.display;
    const isProblemList = c.category?.some((cat: any) =>
      cat.coding?.some((cod: any) => cod.code === "problem-list-item")
    );
    const isPast = c.clinicalStatus?.coding?.some((st: any) => st.code === "resolved") || Boolean(c.abatementDateTime);
    const isFunctional = c.id?.includes("functional");

    if (isFunctional) {
      profile.fs!.push({
        c: coding?.code,
        d_display: display,
        cs: coding?.system,
        dt: c.onsetDateTime,
        st: c.clinicalStatus?.coding?.[0]?.code,
      });
    } else if (isPast && !isProblemList) {
      profile.ph!.push({
        c: coding?.code,
        d_display: display,
        cs: coding?.system,
        dt: c.onsetDateTime,
        ab: c.abatementDateTime,
        d: c.note?.[0]?.text,
      });
    } else {
      profile.cn!.push({
        c: coding?.code,
        d_display: display,
        cs: coding?.system,
        dt: c.onsetDateTime,
        st: c.clinicalStatus?.coding?.[0]?.code,
        d: c.note?.[0]?.text,
      });
    }
  }

  // 5. Immunizations
  const immResources = resources.filter(r => r.resourceType === "Immunization");
  for (const imm of immResources) {
    const coding = imm.vaccineCode?.coding?.[0];
    profile.im!.push({
      c: coding?.code,
      d_display: imm.vaccineCode?.text || coding?.display,
      cs: coding?.system,
      dt: imm.occurrenceDateTime,
      st: imm.status,
      dn: imm.protocolApplied?.[0]?.doseNumberPositiveInt,
    });
  }

  // 6. Procedures
  const procResources = resources.filter(r => r.resourceType === "Procedure");
  for (const pr of procResources) {
    const coding = pr.code?.coding?.[0];
    profile.pr!.push({
      c: coding?.code,
      d_display: pr.code?.text || coding?.display,
      cs: coding?.system,
      dt: pr.performedDateTime,
      st: pr.status,
    });
  }

  // 7. Devices (Device + DeviceUseStatement)
  const devUseResources = resources.filter(r => r.resourceType === "DeviceUseStatement");
  for (const du of devUseResources) {
    const devRef = du.device?.reference;
    const devRes = (devRef && resByUrl.get(devRef)) || (devRef && resById.get(devRef.replace("Device/", "")));
    const coding = devRes?.type?.coding?.[0];
    profile.dv!.push({
      c: coding?.code,
      d_display: devRes?.type?.text || coding?.display,
      cs: coding?.system,
      dt: du.recordedOn,
      st: du.status,
    });
  }

  // 8. Results & Observations
  const obsResources = resources.filter(r => r.resourceType === "Observation");
  for (const obs of obsResources) {
    const coding = obs.code?.coding?.[0];
    const isBlood = coding?.code === LOINC_ABO_RH;
    const isPregnancy = coding?.system?.includes("loinc") && (coding?.code === "10162-6" || coding?.code?.startsWith("116") || coding?.code === "82810-3");

    if (isPregnancy) {
      profile.pg!.push({
        c: coding?.code,
        d_display: obs.code?.text || coding?.display,
        dt: obs.effectiveDateTime,
        v: obs.valueInteger != null ? String(obs.valueInteger) : obs.valueString,
      });
      continue;
    }

    const resEntry: JEntryGeneric = {
      c: coding?.code,
      d_display: obs.code?.text || coding?.display,
      cs: coding?.system,
      dt: obs.effectiveDateTime,
      st: obs.status,
    };

    if (isBlood) {
      const valCoding = obs.valueCodeableConcept?.coding?.[0];
      resEntry.vc = valCoding?.code;
      resEntry.v = obs.valueCodeableConcept?.text || valCoding?.display;
      const detectedBloodGroup = labelFromSnomedCode(resEntry.vc) || normalizeBloodGroup(resEntry.v);
      if (detectedBloodGroup && profile.p) {
        profile.p.bt = detectedBloodGroup;
      }
    } else if (obs.valueQuantity) {
      resEntry.v = String(obs.valueQuantity.value);
      resEntry.u = obs.valueQuantity.unit;
    } else if (obs.valueString) {
      resEntry.v = obs.valueString;
    }

    if (obs.interpretation && obs.interpretation.length > 0) {
      resEntry.ip = obs.interpretation[0].coding?.[0]?.code;
    }

    if (obs.referenceRange && obs.referenceRange.length > 0) {
      resEntry.rr = obs.referenceRange[0].text;
    }

    profile.rs!.push(resEntry);
  }

  return profile;
}
