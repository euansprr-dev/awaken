"""What each power's level changes today (static scan, docs/power-levels/PLAN.md section 2). Run: python3 docs/power-levels/audit_levels.py src/main/java/dev/awaken/powers/catalog"""
import re,sys,os,json
D=sys.argv[1]
BIG={'IMPACT','SLAM','RING','NOVA','CONE','SMOKE','DOME','VORTEX','PALM','ECHO','FROST','SUMMON','GLYPH','STRIKE'}
out=[]
for fn in sorted(os.listdir(D)):
    if not fn.endswith('Powers.java'): continue
    src=open(os.path.join(D,fn)).read()
    # method bodies
    meths={}
    for m in re.finditer(r'\n    (?:private |public |static |final )*[\w<>\[\]., ?@]+ (\w+)\(([^)]*)\)\s*\{',src):
        name=m.group(1); i=m.end(); depth=1
        while depth and i<len(src):
            if src[i]=='{':depth+=1
            elif src[i]=='}':depth-=1
            i+=1
        meths.setdefault(name,'')
        meths[name]+=src[m.end():i]
    for b in re.finditer(r'PowerType\.builder\("(\w+)", "([^"]+)", Family\.(\w+), Slot\.(\w+)\)(.*?)\.register\(\)',src,re.S):
        pid,name,fam,slot,body=b.groups()
        cm=re.search(r'\.(?:cast|passive)\(\w+::(\w+)\)',body)
        meth=cm.group(1) if cm else None
        code=''
        if meth:
            code=meths.get(meth,'')
            # helpers named with the method as prefix, and methods called once from it
            for k,v in meths.items():
                if k!=meth and k.lower().startswith(meth.lower()): code+=v
            for call in set(re.findall(r'\b(\w+)\(c[,)]',code)):
                if call in meths and call!=meth and len(call)>3: code+=meths[call]
        code+=body
        kinds=sorted(set(re.findall(r'FxKind\.([A-Z_]+)',code)))
        out.append(dict(id=pid,name=name,fam=fam,slot=slot,dmg=('c.dmg(' in code or '.dmg()' in code),
            ticks=('c.ticks(' in code),lvl=('powerLevel' in code or 'level,' in body),
            kinds=kinds,grows=sorted(set(kinds)&BIG),snd=len(re.findall(r'c\.sound\(|PowerSounds\.',code)),
            pl=re.findall(r'[^\n]*powerLevel[^\n]*',code)[:2]))
for o in out:
    ch=[]
    if o['dmg']: ch.append('dmg+12%')
    ch.append('cd-4%')
    if o['ticks']: ch.append('dur+5%')
    if o['lvl']: ch.append('own')
    print(f"{o['id']:15} {o['fam'][:5]:5} {o['slot'][:4]:4} {' '.join(ch):28} grows:{','.join(o['grows']) or '-':22} fx:{','.join(o['kinds'])}")
print(len(out))
