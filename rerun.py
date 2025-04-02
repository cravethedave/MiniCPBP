import subprocess
import os

def rerun(name: str):
    job_name = "job_" + name.split('.txt')[0].split('slout_')[-1] + ".sh"
    subprocess.Popen([
        "/bin/sh",
        "-c",
        f"sbatch --output={name} --mem=8G --time=0:15:00 {job_name}"
    ])

slouts = [file for file in os.listdir() if file.startswith('slout_')]

for file in slouts:
    with open(file,'r') as f:
        if f.readlines()[-1].startswith('[ERROR]'):
            rerun(file)