"""
SehatSetu - Application Launcher (Java Spring Boot Backend)
Runs the Java Spring Boot full-stack server on http://127.0.0.1:8000
"""

import subprocess
import os
import sys

if __name__ == "__main__":
    root_dir = os.path.dirname(os.path.abspath(__file__))
    java_backend_dir = os.path.join(root_dir, "java-backend")
    mvnw_path = os.path.join(java_backend_dir, "mvnw.cmd")

    print("=" * 60)
    print("  SehatSetu - Better Care. Simpler Healthcare. (Java Backend)")
    print("=" * 60)
    print("  Starting SehatSetu Java Spring Boot Platform...")
    print("  URL: http://127.0.0.1:8000")
    print("=" * 60)
    print("  Pre-seeded Demo Accounts:")
    print("  * Patient:  aarav.singh@gmail.com     / Patient@123")
    print("  * Doctor:   ananya.sharma@sehatsetu.in / Doctor@123")
    print("  * Admin:    admin@sehatsetu.in         / Admin@123")
    print("=" * 60)

    cmd = [mvnw_path, "spring-boot:run"]
    subprocess.run(cmd, cwd=java_backend_dir)
