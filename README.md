# NTL – Network Time Link

**NTL** is a Kotlin-based Android prototype demonstrating a power-efficient communication and localisation protocol for industrial IoT environments (IIoT).

This project models short-range wireless communication between **battery-powered** and **wired** telematics devices.
Wired devices (e.g., vehicle-mounted telematics units) act as *mobile sinks*, offering nearby nodes access to time, location and cloud connectivity, offloading high-energy tasks like GPS and LTE.

---

## 🧪 Prototype Overview

Current modules include:
- `BatteryApp`: Kotlin Android app representing battery-powered nodes
- `WiredApp`: Kotlin Android app representing wired telematics units
- `Common`: Shared code and utilities used by both apps

The project demonstrates:
- BLE Advertising and GATT communication patterns
- Secure data offloading using ECDH key exchange and AES-GCM encryption
- Device registry concepts

---

## 📘 About NTL

NTL is designed for scenarios where:
- Devices operate under a unified trust framework
- Wired mobile nodes provide reliable relay gateways
- Minor network delay and accuracy trade-offs are acceptable

---

## 🎓 Research Context

This work was conducted as part of the PhD research by **Max Amiri**.
The concept has been published and can be cited as:

> **Energy-Efficient Communication and Localisation Protocol for Industrial IoT Devices**
> Max Amiri, David Eyers, and Morteza Biglari-Abhari
> In *Proceedings of the 9th IEEE International Conference on Smart Internet of Things (SmartIoT 2025)*,
> 17–20 November 2025, Sydney, Australia.

### 📑 BibTeX Citation

```bibtex
@inproceedings{Amiri2025NTL,
  title={Energy-Efficient Communication and Localisation Protocol for Industrial IoT Devices},
  author={Amiri, Max and Eyers, David and Biglari-Abhari, Morteza},
  booktitle={Proceedings of the 9th IEEE International Conference on Smart Internet of Things (SmartIoT)},
  year={2025},
  month={November},
  address={Sydney, Australia}
}
```

---

## 📬 Contact

For collaboration opportunities, please reach out to Max Amiri.

---

> Made with Kotlin. Built for industrial IoT research.