#!/usr/bin/env python3
import subprocess


# minWt, maxWt, minC, maxC, #cycles, #branches
# Results are domWdeg and maxMarginal ??m??s - ??????, ??.??s - ?????
test_cases = [
    ["2996","3000","35","65","1","2"], # ??m??s - ???????, ??.??s - ?????
    # ["2996","3000","35","65","0","4"], # ??m??s - ???????, ??.??s - ?????
    # ["2975","3050","10","90","1","3"], # ??m??s - ???????, 02m55s - 46202
]

methods = ["domWdeg", "maxMarginal", "minEntropy", "rnd"]
base_line = "#!/bin/bash\nmodule load maven\nmvn compile -q\n"
base_command = "mvn exec:java -Dexec.mainClass='minicpbp.examples.TestGrammar' -q -Dexec.args="

for method in methods:
    for index, test in enumerate(test_cases):
        arguments = f"{method} {' '.join(test)}"
        info_print = f"echo {arguments}\n"
        command = f"{base_command}'{arguments}'"
        file_content = base_line+info_print+command
        
        name = f"job_{method}_{index}.sh"
        with open(name, 'w') as f:
            f.write(file_content)
            f.close()
        
        subprocess.call(["chmod", "+x", name])
        subprocess.Popen([
            "/bin/sh",
            "-c",
            f"sbatch {name}"
        ])

# print(arguments)
# p = subprocess.Popen([
#     "/bin/sh",
#     "-c",
#     f"mvn exec:java -Dexec.mainClass='minicpbp.examples.TestGrammar' -Dexec.args='{arguments}'"
# ],)

# p.wait()
# p.kill()