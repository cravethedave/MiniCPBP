import subprocess
import os

def rerun(name: str):
    job_name = "job_" + name.split('.txt')[0].split('slout_')[-1] + ".sh"
    subprocess.Popen([
        "/bin/sh",
        "-c",
        f"sbatch --output={name} --mem=1G --time=1:00:00 {job_name}"
    ])

slouts = []
for file in os.listdir():
    if file.startswith('slout_'):
        slouts.append(file)
        
for file in slouts:
    with open(file,'r') as f:
        if f.readlines()[-1].startswith('[ERROR]'):
            rerun(file)