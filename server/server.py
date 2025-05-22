import torch
from flask import Flask, request
from sample_creator import tokenize
from transformers import GPT2TokenizerFast, GPT2LMHeadModel, DataCollatorWithPadding#, pipeline

tokenizer = GPT2TokenizerFast.from_pretrained("entropy/gpt2_zinc_87m", max_len=40)
model = GPT2LMHeadModel.from_pretrained('entropy/gpt2_zinc_87m')
collator = DataCollatorWithPadding(tokenizer, padding=True, return_tensors='pt')

TOKENS = {
    'F',
    'Cl',
    'Br',
    'I',
    'O',
    'N',
    'S',
    'C',
    '[',
    ']',
    '-',
    '+',
    '@',
    'H',
    'H3',
    '1',
    '2',
    '3',
    '4',
    '5',
    '6',
    '7',
    '8',
    '=',
    '/',
    '\\',
    '(',
    ')',
    '#',
    'o',
    'n',
    's',
    'c',
    '</s>'
}

app = Flask(__name__)

@app.route('/token', methods=['POST'])
def new_token():
    molecule = request.data.decode("utf-8")
    # Encode molecule
    inputs = tokenizer.encode(molecule, return_tensors="pt")

    # Get probabilities for next tokens
    with torch.no_grad():
        outputs = model(inputs)
        predictions = outputs[0]
    
    gpt_probabilities = torch.nn.functional.softmax(predictions[0,-1,:], dim=-1).tolist()
    gpt_tokens: dict[str,float] = {tokenizer.decode([idx]).strip():gpt_probabilities[idx] for idx in range(len(gpt_probabilities))}
    
    probs = {iter:0 for iter in TOKENS}
    for g_t in gpt_tokens.keys():
        split_ngram = tokenize(g_t)
        if len(split_ngram) == 0: # Skip empty strings
            continue
        t = split_ngram[0] # Get the first token from the ngram
        if t not in TOKENS: # Skip if the token is not in our grammar
            continue
        probs[t] += gpt_tokens[g_t]
    summed_values = sum(probs.values())
    probs = {k:v/summed_values for k,v in probs.items()}
    probs['_'] = probs.pop('</s>') # replace end token by our padding
    return probs