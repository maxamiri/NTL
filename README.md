# NTL – Network Time Link

**NTL** is a Kotlin-based Android prototype demonstrating a power-efficient communication and localisation protocol for industrial IoT environments (IIoT).

This sample models short-range wireless communication between **battery-powered** and **wired** telematics devices.
Wired devices (e.g. vehicle-mounted telematics units) act as *mobile sinks*, offering nearby nodes access to location and cloud connectivity, offloading high-energy tasks like GPS and LTE.

---

## 🚧 Project Status

This repository is currently a placeholder.

🔒 **The Android source code for NTL will be published here once the main paper is accepted for publication.**

NTL is part of an academic research project focusing on:
- Energy-efficient communication in IoT systems
- Short-range wireless relays between nearby devices
- Secure data offloading using Bluetooth Low Energy (BLE)

---

## 🧪 Prototype Overview

Once the main paper is published, the repository will include:
- Kotlin-based Android apps for both wired and battery-powered roles
- Sample implementation for *Device Registry*
- BLE Advertising and GATT communication reference code
- ECDH key exchange and AES-GCM encryption
- SHA-256 checksums for data integrity

The apps demonstrate how proximity-based relaying can achieve **significant power savings** over GPS+LTE-based communication.

---

## 📘 About NTL

NTL is designed for environments where:
- Devices are managed under a unified trust framework
- Wired mobile nodes are dense enough to act as relay gateways
- Minor network delay and accuracy trade-offs are acceptable

---

## 📬 Contact

For academic enquiries, please email the authors after the source is released.

---

> Made with Kotlin. Built for industrial IoT research.
