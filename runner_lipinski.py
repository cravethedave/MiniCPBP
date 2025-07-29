#!/usr/bin/env python3
import subprocess
import time

BASE_LINE = "#!/bin/bash\nmodule load maven\nexport JAVA_TOOL_OPTIONS=-Xmx4g\nmvn compile -q\n"
# BASE_COMMAND = "mvn exec:java -Dexec.mainClass='minicpbp.examples.molecules.TestGrammarV7' -q -Dexec.args="
BASE_COMMAND = "mvn exec:java -q -Dexec.args="
TIME = '0:35:00'
MEM = '8G'

# minWt, maxWt, minC, maxC, #cycles, #branches
def cc_heuristic_runner(methods, test_cases, size=20, diff=''):
    print("Starting heuristic jobs.")
    for method in methods:
        for index, test in enumerate(test_cases):
            arguments = f"{size} {method} {' '.join(test)}"
            info_print = f"echo {arguments}\n"
            command = f"{BASE_COMMAND}'{arguments}'"
            file_content = BASE_LINE+info_print+command
            
            identifier = f"{size}_{method}"
            if test[0] == "true":
                identifier += "_lip"
            if test[1] == "true":
                identifier += f"_smpl_k{test[2]}"
            if test[3] != "0":
                identifier += f"_{test[3]}sols"
            if test[4] != "0":
                identifier += f"_{test[4]}secs"
            
            # weight
            identifier += f"_weight[{test[5]}-{test[6]}]"
            
            # logP
            identifier += f"_logP[{test[7]}-{test[8]}]"
            
            # cycles
            identifier += f"_c{test[9]}"
            
            # branches
            identifier += f"_c{test[10]}"
            
            # regular
            if test[11] == 'true':
                identifier += "_regular"
            
            name = f"job_{identifier}.sh"
            with open(name, 'w') as f:
                f.write(file_content)
            
            subprocess.call(["chmod", "+x", name])
            subprocess.Popen([
                "/bin/sh",
                "-c",
                f"sbatch --output=slout_{identifier}.txt --mem={MEM} --time={TIME} {name}"
            ])
            time.sleep(0.1) # prevents overloading compute canada
            
    print("Done queueing heuristic jobs.")

def cc_random_runner(test_cases, method='rnd', size=20, diff=''):
    print(f"Starting {method} job.")
    for index, test in enumerate(test_cases):
        arguments = f"{size} {method} {' '.join(test)}"
        info_print = f"echo {arguments}\n"
        command = f"{BASE_COMMAND}'{arguments}'"
        file_content = BASE_LINE+info_print+command
        
        for i in range(11):
            identifier = f"{size}_{method}"
            if test[0] == "true":
                identifier += "_lip"
            if test[1] == "true":
                identifier += f"_smpl_k{test[2]}"
            if test[3] != "0":
                identifier += f"_{test[3]}sols"
            if test[4] != "0":
                identifier += f"_{test[4]}secs"
                        
            # weight
            identifier += f"_weight[{test[5]}-{test[6]}]"
            
            # logP
            identifier += f"_logP[{test[7]}-{test[8]}]"
            
            # cycles
            identifier += f"_c{test[9]}"
            
            # branches
            identifier += f"_c{test[10]}"
            
            # regular
            if test[11] == 'true':
                identifier += "_regular"
            
            identifier += f"_{i}"
            name = f"job_{identifier}.sh"
            with open(name, 'w') as f:
                f.write(file_content)
                f.close()
            
            subprocess.call(["chmod", "+x", name])
            subprocess.Popen([
                "/bin/sh",
                "-c",
                f"sbatch --output=slout_{identifier}.txt --mem={MEM} --time={TIME} {name}"
            ])
            time.sleep(0.1) # prevents overloading compute canada
    print("Done queueing random jobs.")

def home_runner(method, test, size=20):
    arguments = f"{size} {method} {' '.join(test)}"
    print(arguments)
    command = f"{BASE_COMMAND}'{arguments}'"
    p = subprocess.Popen([
        "/bin/sh",
        "-c",
        f"{command}"
    ])
    p.wait()
    p.kill()

def run_failed(test_cases):
    for i,test in enumerate(test_cases):
        arguments = ' '.join(test)
        info_print = f"echo {arguments}\n"
        command = f"{BASE_COMMAND}'{arguments}'"
        file_content = BASE_LINE+info_print+command
        
        identifier = f"{'_'.join(test)}_{i}"
        name = f"job_{identifier}.sh"
        with open(name, 'w') as f:
            f.write(file_content)
            f.close()
        
        subprocess.call(["chmod", "+x", name])
        subprocess.Popen([
            "/bin/sh",
            "-c",
            f"sbatch --output=slout_{identifier}.txt --mem={MEM} --time={TIME} {name}"
        ])
        time.sleep(1) # prevents overloading compute canada
    print("Done queueing failed jobs.")

# doLipinski, doSampling, sampleExponent, #sols, limitInSeconds, #cycles, #branches
# Results are domWdeg and maxMarginal ??m??s - ??????, ??.??s - ?????
test_cases = [
    # ["lpsk","sampl","k","#","time","minW","maxW","minL","maxL", "rglr"]
    # ["true","false","2","1","1800","1750","2250","-400","-300", "true"],
    # ["true","false","2","1","1800","1750","2250","-200","-100", "true"],
    # ["true","false","2","1","1800","1750","2250","100" ,"200" , "true"],
    # ["true","false","2","1","1800","2750","3250","-400","-300", "true"],
    # ["true","false","2","1","1800","2750","3250","-200","-100", "true"],
    # ["true","false","2","1","1800","2750","3250","100" ,"200" , "true"],
    # ["true","false","2","1","1800","3750","4250","-400","-300", "true"],
    # ["true","false","2","1","1800","3750","4250","-200","-100", "true"],
    # ["true","false","2","1","1800","3750","4250","100" ,"200" , "true"],
    # ["true","false","2","1","1800","1750","2250","-400","-300", "false"],
    # ["true","false","2","1","1800","1750","2250","-200","-100", "false"],
    # ["true","false","2","1","1800","1750","2250","100" ,"200" , "false"],
    # ["true","false","2","1","1800","2750","3250","-400","-300", "false"],
    # ["true","false","2","1","1800","2750","3250","-200","-100", "false"],
    # ["true","false","2","1","1800","2750","3250","100" ,"200" , "false"],
    # ["true","false","2","1","1800","3750","4250","-400","-300", "false"],
    # ["true","false","2","1","1800","3750","4250","-200","-100", "false"],
    # ["true","false","2","1","1800","3750","4250","100" ,"200" , "false"],
#   ["lpsk","sampl","k","#","time","minW","maxW","minL","maxL","c","b","rglr"]
    # ["true","false","2","1","1800","0000","5000","-400","500" ,"1","2","true"],
    # ["true","false","2","1","1800","0000","5000","-400","500" ,"1","3","true"],
    # ["true","false","2","1","1800","0000","5000","-400","500" ,"1","4","true"],
    # ["true","false","2","1","1800","0000","5000","-400","500" ,"2","2","true"],
    # ["true","false","2","1","1800","0000","5000","-400","500" ,"2","3","true"],
    # ["true","false","2","1","1800","0000","5000","-400","500" ,"2","4","true"],
    # ["true","false","2","1","1800","0000","5000","-400","500" ,"3","2","true"],
    # ["true","false","2","1","1800","0000","5000","-400","500" ,"3","3","true"],
    # ["true","false","2","1","1800","0000","5000","-400","500" ,"3","4","true"],
]

arguments = {
    'lipinski':         ['true'],
    'sampling':         ['false'],
    'k':                ['2'],
    'solutions':        ['1'],
    'time':             ['1800'],
    'weightRange':      [('0','5000')],
    'lipinskiRange':    [('-400','500')],
    'cycles':           ['1','2','3'],
    'branches':         ['2','3','4'],
    'regular':          ['true']
}
tests_to_add = [[]]
for key in arguments.keys():
    new_tests = []
    for test in tests_to_add:
        for value in arguments[key]:
            new_tests.append(test)
            if value is not str and len(value) == 2:
                new_tests[-1].append(value[0])
                new_tests[-1].append(value[1])
            else:
                new_tests[-1].append(value)
    tests_to_add = new_tests
test_cases.extend(tests_to_add)


arguments = {
    'lipinski':         ['true'],
    'sampling':         ['false'],
    'k':                ['2'],
    'solutions':        ['1'],
    'time':             ['1800'],
    'weightRange':      [('1750','2250'),('2750','3250'),('3750','4250')],
    'lipinskiRange':    [('-400','-300'),('-200','-100'),('100','200')],
    'cycles':           ['1','2','3'],
    'branches':         ['2','3','4'],
    'regular':          ['true']
}
tests_to_add = [[]]
for key in arguments.keys():
    new_tests = []
    for test in tests_to_add:
        for value in arguments[key]:
            new_tests.append(test)
            if value is not str and len(value) == 2:
                new_tests[-1].append(value[0])
                new_tests[-1].append(value[1])
            else:
                new_tests[-1].append(value)
    tests_to_add = new_tests
test_cases.extend(tests_to_add)

print(f"Running {len(test_cases)} test cases")

methods = [
    # "domWdeg",
    # "domWdegLDS",
    # "domWdegRandom",
    # "domWdegMaxMarginalValue",
    # "dom-random",
    "maxMarginal",
    # "maxMarginalRestart",
    "maxMarginalLDS",
    "maxMarginalStrength",
    "maxMarginalStrengthLDS",
    # "maxMarginalStrengthBiasedWheelSelectVal",
    # "firstFailMaxMarginalValue",
    # "lexicoMarginal",
    # "impact",
    # "impactRestart",
    # "impactLDS",
    # "minEntropy",
    # "minEntropyLDS",
    # "impactMinVal",
    # "impactMinValRestart",
    # "minEntropyBiasedWheel",
]

print(methods)
cc_heuristic_runner(methods, test_cases, size=40)
cc_random_runner(test_cases, method='domWdegRandom', size=40)
# cc_random_runner(test_cases, method='maxMarginalStrengthBiasedWheelSelectVal', size=40)
# cc_random_runner(test_cases, method='dom-random', size=40)

failed = []

# cc_heuristic_runner(methods, test_cases, size=30)
# run_failed(failed)
print("Have a nice day!")