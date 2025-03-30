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

# fill_mask = pipeline(
#     "fill-mask",
#     model='seyonec/ChemBERTa-zinc-base-v1',
#     tokenizer='seyonec/ChemBERTa-zinc-base-v1'
# )

@app.route('/')
def testing():
    return "hei"

@app.route('/ngrams', methods=['POST'])
def ngrams():
    pass

# @app.route('/token', methods=['POST'])
def next_token():
    # print("##################### New Request #####################")
    current_mol = request.data.decode("utf-8")
    print(current_mol)
    inputs = torch.tensor([tokenizer(current_mol)['input_ids']])
    outputs = model.generate(
        inputs,
        do_sample=True,
        max_length=40,
        temperature=1.5,
        max_new_tokens=1,
        # early_stopping=True,
        return_dict_in_generate=True,
        output_scores=True,
        pad_token_id=tokenizer.pad_token_id,
        num_return_sequences=512
    )
    transition_scores = torch.exp(model.compute_transition_scores(outputs.sequences, outputs.scores, normalize_logits=True)).flatten().tolist()
    generated_tokens = [tokenizer.decode(token) for token in outputs.sequences[:, -1:].flatten()]
    big_token_prob = {generated_tokens[i]:transition_scores[i] for i in range(len(generated_tokens))}
    real_token_prob = {}

    for k,v in big_token_prob.items():
        token = tokenize(k)[0]
        # if token.isdigit():
        #     if token not in real_token_prob.keys():
        #         for i in range(1,9):
        #             real_token_prob[str(i)] = 0
        #     for i in range(1,9):
        #         real_token_prob[str(i)] += v
        #     continue
        if token.startswith('</s'):
            token = '_'
        if token not in real_token_prob.keys():
            real_token_prob[token] = 0
        real_token_prob[token] += v
    
    summed_prob = sum(real_token_prob.values())
    for k,v in real_token_prob.items():
        real_token_prob[k] = v/summed_prob
    
    return real_token_prob

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

# raw_probs = fill_mask("<s>CCCC<mask>",top_k=100)
# raw_probs = {p['token_str']:p['score'] for p in raw_probs}

# prob_dict = {}
# for k,v in raw_probs.items():
#     token = tokenize(k)[0]
#     if token not in TOKENS:
#         print(f"WARNING Unknown token {token}")
#         continue
#     if token not in prob_dict.keys():
#         prob_dict[token] = 0
#     prob_dict[token] += v

# biggest_keys = sorted(prob_dict.keys(), key=lambda x: prob_dict[x], reverse=True)
# biggest_keys = biggest_keys[:max(100,len(biggest_keys))]

# final_dict = {k:prob_dict[k] for k in biggest_keys}
# print(final_dict)
# Traitement